/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.CREATED_DATE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.SignFileCleanupJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(SignFileCleanupJobProperties::class)
class SignFileCleanupJob(
    private val nodeClient: NodeClient,
    val properties: SignFileCleanupJobProperties
) : DefaultContextMongoDbJob<SignFileCleanupJob.SignFileData>(properties) {

    private val expiredOfDays: Long
        get() = properties.expireOfDays.toLong()

    override fun start(): Boolean {
        return super.start()
    }

    override fun collectionNames(): List<String> {
        return listOf(TABLE_NAME)
    }

    override fun buildQuery(): Query {
        val expiredDate = LocalDateTime.now().minusDays(expiredOfDays)
        return Query(Criteria.where(CREATED_DATE).lt(expiredDate))
    }

    override fun run(row: SignFileData, collectionName: String, context: JobContext) {
        with(row) {
            val deleteReq = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                operator = SYSTEM_USER
            )
            nodeClient.deleteNode(deleteReq)
            mongoTemplate.remove(Query(Criteria(ID).isEqualTo(id)), collectionName)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): SignFileData {
        return SignFileData(row)
    }

    override fun entityClass(): KClass<SignFileData> {
        return SignFileData::class
    }

    override fun getLockAtMostFor(): Duration {
        return Duration.ofHours(1)
    }

    data class SignFileData(private val map: Map<String, Any?>) {
        val id: String? by map
        val projectId: String by map
        val repoName: String by map
        val fullPath: String by map
    }

    companion object {
        const val TABLE_NAME = "sign_file"
    }
}
