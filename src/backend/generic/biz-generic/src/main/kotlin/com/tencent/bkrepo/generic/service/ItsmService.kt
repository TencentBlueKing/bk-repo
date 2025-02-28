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

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.security.interceptor.devx.QueryResponse
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.generic.config.ItsmProperties
import com.tencent.bkrepo.generic.pojo.share.ItsmTicket
import com.tencent.bkrepo.generic.pojo.share.ItsmTicketCreateRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(SyncCondition::class)
class ItsmService(
    private val itsmProperties: ItsmProperties,
) {

    private val httpClient = HttpClientBuilderFactory.create().build()

    fun createTicket(
        fields: List<Map<String, Any>>,
        serviceId: Int,
        approvalStateId: Int,
        approvalUsers: List<String>
    ): ItsmTicket {
        val userId = SecurityUtils.getUserId()
        val url = itsmProperties.url
        val body = ItsmTicketCreateRequest(
            serviceId = serviceId,
            creator = userId,
            fields = fields,
            meta = mapOf(
                "state_processors" to mapOf(approvalStateId to approvalUsers.joinToString(",")),
            )
        )
        val request = Request.Builder()
            .url(url)
            .addHeader("x-bkapi-authorization", headerStr())
            .addHeader("x-devops-uid", userId)
            .post(body.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull()))
            .build()
        logger.info("createTicket|$url|$body")
        val resp = request(request, url, body)
        return resp.data!!
    }

    private fun request(
        request: Request,
        url: String,
        body: ItsmTicketCreateRequest
    ) = try {
        httpClient.newCall(request).execute().use { response ->
            val data = response.body!!.string()
            if (!response.isSuccessful) {
                logger.error("createTicket|$url|$body|${response.code}|$data")
                throw ErrorCodeException(
                    CommonMessageCode.SERVICE_CALL_ERROR,
                )
            }
            logger.debug("createTicket|$url|$data")
            val resp = data.readJsonString<QueryResponse<ItsmTicket>>()
            if (resp.status != 0) {
                logger.error("createTicket|$url|$body|${response.code}|$data")
                throw ErrorCodeException(
                    CommonMessageCode.SERVICE_CALL_ERROR,
                )
            }
            resp
        }
    } catch (e: ErrorCodeException) {
        throw e
    } catch (e: Exception) {
        logger.error("createTicket request error", e)
        throw ErrorCodeException(
            CommonMessageCode.SERVICE_CALL_ERROR,
        )
    }


    private fun headerStr(): String {
        return mapOf(
            "bk_app_code" to itsmProperties.appCode,
            "bk_app_secret" to itsmProperties.appSecret,
            "bk_username" to SecurityUtils.getUserId()
        ).toJsonString().replace("\\s".toRegex(), "")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ItsmService::class.java)
    }
}
