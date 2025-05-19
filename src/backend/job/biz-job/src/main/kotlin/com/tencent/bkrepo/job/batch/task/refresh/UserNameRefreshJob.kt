/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.task.refresh

import com.tencent.bkrepo.common.api.constant.TENANT_ID
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.properties.EnableMultiTenantProperties
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.UserNameRefreshJobProperties
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
@EnableConfigurationProperties(UserNameRefreshJobProperties::class)
class UserNameRefreshJob (
    private val mongoTemplate: MongoTemplate,
    private val userinfoRefreshJobProperties: UserNameRefreshJobProperties = UserNameRefreshJobProperties(),
    private val enableMultiTenantProperties: EnableMultiTenantProperties = EnableMultiTenantProperties()
): DefaultContextJob(userinfoRefreshJobProperties) {

    data class UserInfoRecord(
        val userId: String,
        var name: String,
    )

    data class PersonInfo(
        val bk_username: String,
        val login_name: String,
        val full_name: String,
        val display_name: String
    )

    data class QueryResult(
        val data: List<PersonInfo>
    )

    private val httpClient = HttpClientBuilderFactory.create().build()

    /**
     * 由于接口参数最大100，所以分次请求
     */
    override fun doStart0(jobContext: JobContext) {
        if (enableMultiTenantProperties.enabled) {
            val users =mongoTemplate.find(Query(), UserInfoRecord::class.java, USER_INFO_COLLECTION )
            val usersParams = users.chunked(PARAM_LIMIT)
            logger.info("refresh user name job start")
            for (param in usersParams) {
                val requestParam = param.joinToString(",") { it.name }
                queryAndSwapName(requestParam, param)
            }
        }
    }

    private fun queryAndSwapName(param: String, users: List<UserInfoRecord>) {
        val baseUrl = userinfoRefreshJobProperties.displayNameBaseUrl
        val api = userinfoRefreshJobProperties.getDisplayNameApi
        val url = "$baseUrl$api?bk_usernames=$param"
        val tenantId = ProjectServiceHelper.getTenantId()?: return
        val request = Request.Builder().url(url).header(TENANT_ID, tenantId)
            .header("X-Bkapi-Authorization", headerStr())
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseContent = response.body!!.string()
                logger.info("response code is ${response.code}, responseContent is $responseContent")
                if (!response.isSuccessful) return
                val target = swapNames(responseContent, users)
                if (target.isNotEmpty()) updateNames(target)
            }
        } catch (_: Exception) {
            logger.error("Request to change name failed")
        }
    }

    private fun headerStr(): String {
        return mapOf(
            "bk_token" to userinfoRefreshJobProperties.bkToken
        ).toJsonString().replace("\\s".toRegex(), "")
    }

    private fun swapNames(responseContent: String, origin: List<UserInfoRecord>): List<UserInfoRecord> {
        try {
            val result = mutableListOf<UserInfoRecord>()
            val res = JsonUtils.objectMapper.readValue(responseContent, QueryResult::class.java)
            res.data.forEach { personInfo ->
                val matchRecord = origin.find { it.name == personInfo.bk_username }
                if (matchRecord != null) {
                    result.add(UserInfoRecord(
                        userId = matchRecord.userId,
                        name = personInfo.display_name
                    ))
                }
            }
            if (result.isEmpty()) return emptyList()
            return result
        } catch (e: Exception) {
            logger.error("Response conversion failed", e)
            return emptyList()
        }
    }

    private fun updateNames(users: List<UserInfoRecord>) {
        val bulkOps = mongoTemplate.bulkOps(
            BulkOperations.BulkMode.ORDERED,
            UserInfoRecord::class.java,
            USER_INFO_COLLECTION)
        users.forEach { user ->
            val query = Query.query(Criteria.where("userId").`is`(user.userId))
            val update = Update().set("name", user.name)
            bulkOps.updateOne(query, update)
        }
        bulkOps.execute()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserNameRefreshJob::class.java)
        private const val USER_INFO_COLLECTION = "user"
        private const val PARAM_LIMIT = 100
    }

}

