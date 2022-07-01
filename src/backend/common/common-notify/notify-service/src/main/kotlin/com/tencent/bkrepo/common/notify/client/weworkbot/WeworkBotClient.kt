/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyChannelCredential
import com.tencent.bkrepo.common.notify.api.NotifyMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotMessage
import com.tencent.bkrepo.common.notify.client.NotifyClient
import com.tencent.bkrepo.common.notify.config.NotifyProperties
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory

class WeworkBotClient(
    private val okHttpClient: OkHttpClient,
    private val notifyProperties: NotifyProperties
) : NotifyClient {

    override fun send(credential: NotifyChannelCredential, message: NotifyMessage) {
        require(credential is WeworkBotChannelCredential && message is WeworkBotMessage)

        // 构造body
        val bodyMap = HashMap<String, Any>().apply {
            if (!message.chatIds.isNullOrEmpty()) {
                put("chatid", message.chatIds!!.joinToString("|"))
            }
            put("msgtype", message.body.type())
            put(message.body.type(), message.body)
        }
        val body = RequestBody.create(MediaType.parse(MediaTypes.APPLICATION_JSON), bodyMap.toJsonString())

        // 构造url
        val host = notifyProperties.weworkApiHost.ifEmpty { DEFAULT_API_HOST }
        val url = HttpUrl.parse("$host$API_SEND_MESSAGE")!!
            .newBuilder()
            .addQueryParameter("key", credential.key)
            .build()

        // 发送请求
        val request = Request.Builder().url(url).post(body).build()
        okHttpClient.newCall(request).execute().use {
            val bodyContent = it.body()?.string()
            if (!it.isSuccessful || bodyContent?.readJsonString<WeworkBotResponse>()?.errCode != 0) {
                logger.error(
                    "send wework bot message failed, " +
                        "notifyChannelName[${credential.name}], res[$bodyContent]"
                )
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
    }
}
