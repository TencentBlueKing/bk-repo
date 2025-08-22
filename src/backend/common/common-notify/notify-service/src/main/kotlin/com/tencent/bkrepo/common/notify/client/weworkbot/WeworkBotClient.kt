/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.notify.client.weworkbot

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyChannelCredential
import com.tencent.bkrepo.common.notify.api.NotifyMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotMessage
import com.tencent.bkrepo.common.notify.client.NotifyClient
import com.tencent.bkrepo.common.notify.config.NotifyProperties
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory

class WeworkBotClient(
    private val okHttpClient: OkHttpClient,
    private val notifyProperties: NotifyProperties
) : NotifyClient {

    override fun send(credential: NotifyChannelCredential, message: NotifyMessage) {
        require(credential is WeworkBotChannelCredential && message is WeworkBotMessage)
        if (message.chatIds.isNullOrEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "chatIds is empty")
        }

        // 构造body
        val bodyMap = HashMap<String, Any>().apply {
            put("chatid", message.chatIds!!.joinToString("|"))
            put("msgtype", message.body.type())
            put(message.body.type(), message.body)
        }
        val body = bodyMap.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())

        // 构造url
        val host = notifyProperties.weworkApiHost.ifEmpty { DEFAULT_API_HOST }
        val url = "$host$API_SEND_MESSAGE".toHttpUrlOrNull()!!
            .newBuilder()
            .addQueryParameter("key", credential.key)
            .build()

        // 发送请求
        val request = Request.Builder().url(url).post(body).build()
        okHttpClient.newCall(request).execute().use {
            logErr(credential.name, it)
        }
    }

    private fun logErr(credentialName: String, res: Response) {
        val bodyContent = res.body?.string()
        val errMsg = "send wework bot message failed, notifyChannelName[$credentialName], res[$bodyContent]"
        if (!res.isSuccessful) {
            logger.error(errMsg)
        } else {
            val weworkBotResponse = bodyContent?.readJsonString<WeworkBotResponse>()
            if (weworkBotResponse?.errCode == ERR_CODE_INVALID_CHAT_ID) {
                logger.warn(errMsg)
            } else if (weworkBotResponse?.errCode != 0) {
                logger.error(errMsg)
            }
        }
    }

    data class WeworkBotResponse(
        @JsonProperty("errcode")
        val errCode: Int = 0,
        @JsonProperty("errmsg")
        val errMsg: String
    )

    companion object {
        private val logger = LoggerFactory.getLogger(WeworkBotClient::class.java)
        private const val DEFAULT_API_HOST = "https://qyapi.weixin.qq.com"
        private const val API_SEND_MESSAGE = "/cgi-bin/webhook/send"
        private const val ERR_CODE_INVALID_CHAT_ID = 93006
    }
}
