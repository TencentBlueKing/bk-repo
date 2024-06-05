/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.security.manager.ci

import com.sun.org.slf4j.internal.LoggerFactory
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.MessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.devops.api.http.HttpHeaders
import com.tencent.devops.api.pojo.Response
import okhttp3.Request
import okio.IOException
import org.springframework.beans.factory.BeanFactory
import java.util.concurrent.TimeUnit

open class CIPermissionManager(
    beanFactory: BeanFactory,
    private val ciPermissionProperties: CIPermissionProperties
) {

    private val httpClient = HttpClientBuilderFactory.create(beanFactory = beanFactory)
        .connectTimeout(10L, TimeUnit.SECONDS)
        .readTimeout(10L, TimeUnit.SECONDS)
        .build()

    fun checkPipelineRunningStatus(
        projectId: String,
        pipelineId: String,
        buildId: String?,
        taskId: String? = null
    ): PipelineBuildStatus {
        if (!ciPermissionProperties.enabled) {
            return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
        }
        var url = "${ciPermissionProperties.host}/ms/process/api/open/service/pipeline/get_build_status?" +
            "projectId=${projectId}&pipelineId=${pipelineId}&buildId=${buildId}"
        if (!taskId.isNullOrEmpty()) {
            url += "&taskId=$taskId"
        }
        val request = Request.Builder().url(url).header(DEVOPS_TOKEN, ciPermissionProperties.token)
            .header(DEVOPS_PROJECT_ID, projectId)
            .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_JSON)
            .build()
        try {
            httpClient.newCall(request).execute().use {
                if (!it.isSuccessful) {
                    logger.error("query pipeline status failed, response code ${it.code}")
                    return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
                }
                val response = it.body!!.string().readJsonString<Response<PipelineBuildStatus>>()
                if (response.data?.status == "RUNNING") {
                    return response.data!!
                }
                throwOrLogError(CommonMessageCode.PIPELINE_NOT_RUNNING,
                    "$projectId/$pipelineId/$buildId")
                return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
            }
        } catch (e: IOException) {
            logger.error("query pipeline status failed:", e)
        }
        return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
    }

    fun throwOrLogError(messageCode: MessageCode, vararg params: Any) {
        val url = HttpContextHolder.getRequestOrNull()?.requestURI
        val user = SecurityUtils.getPrincipal()
        val userAgent = HeaderUtils.getHeader(HttpHeaders.USER_AGENT)
        val msg = LocaleMessageUtils.getLocalizedMessage(messageCode, params)
        logger.warn("user[$user] illegal pipeline artifact request[$url], error: $msg, user agent: $userAgent")
        if (ciPermissionProperties.returnError) {
            throw ErrorCodeException(messageCode, params = params)
        }
    }

    fun whiteListRequest(): Boolean {
        val platformId = SecurityUtils.getPlatformId()
        return ciPermissionProperties.platformIdWhiteList.contains(platformId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CIPermissionManager::class.java)
        private const val DEVOPS_TOKEN = "X-DEVOPS-BK-TOKEN"
        private const val DEVOPS_PROJECT_ID = "X-DEVOPS-PROJECT-ID"

        const val METADATA_PROJECT_ID = "projectId"
        const val METADATA_PIPELINE_ID = "pipelineId"
        const val METADATA_BUILD_ID = "buildId"
        const val METADATA_BUILD_NO = "buildNo"
        const val METADATA_TASK_ID = "taskId"
        const val METADATA_USER_ID = "sys_uid"
        const val METADATA_OVERWRITE_COUNT = "sys_oc"
        const val METADATA_UPLOAD_CHANNEL = "sys_ch"
        const val METADATA_SUB_PROJECT_ID = "sub_project_id"
        const val METADATA_SUB_PIPELINE_ID = "sub_pipeline_id"
        const val METADATA_SUB_BUILD_ID = "sub_build_id"
        const val METADATA_SUB_BUILD_NO = "sub_build_no"
        const val METADATA_FOLDER_BUILD_ID = "bk_ci_bid" // 标识‘归档文件夹’下的子文件关系，不会被“构件列表”查询出来
        val PIPELINE_METADATA = listOf(
            METADATA_PROJECT_ID, METADATA_PIPELINE_ID, METADATA_BUILD_ID, METADATA_BUILD_NO, METADATA_TASK_ID,
            METADATA_USER_ID, METADATA_OVERWRITE_COUNT, METADATA_UPLOAD_CHANNEL
        )
    }
}
