package com.tencent.bkrepo.common.notify.client.bkci

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyChannelCredential
import com.tencent.bkrepo.common.notify.api.NotifyMessage
import com.tencent.bkrepo.common.notify.api.bkci.BkciChannelCredential
import com.tencent.bkrepo.common.notify.api.bkci.BkciMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.MarkdownMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.TextMessage
import com.tencent.bkrepo.common.notify.client.NotifyClient
import com.tencent.bkrepo.common.notify.config.NotifyProperties
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory

class BkciNotifyClient(
    private val okHttpClient: OkHttpClient,
    private val notifyProperties: NotifyProperties
) : NotifyClient {
    override fun send(credential: NotifyChannelCredential, message: NotifyMessage) {
        require(credential is BkciChannelCredential && message is BkciMessage)
        val content = when (val messageBody = message.body) {
            is MarkdownMessage -> messageBody.content
            is TextMessage -> messageBody.content
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
        }
        val requestBody = WeworkRobotNotifyMessage(
            receivers = message.chatIds!!.joinToString("|"),
            receiverType = message.receiverType,
            textType = WeworkTextType.valueOf(message.body.type()),
            message = content,
            attachments = null
        )

        val req = Request.Builder()
            .url("${notifyProperties.devopsApigw}/v4/apigw-app/notify/wework_robot")
            .header("X-Bkapi-Authorization", authorizationStr(credential))
            .post(requestBody.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()))
            .build()

        try {
            okHttpClient.newCall(req).execute().use { checkRes(it) }
        } catch (e: Exception) {
            logger.error("send notify by bkci failed", e)
        }
    }

    private fun checkRes(res: Response) {
        if (!res.isSuccessful) {
            logger.error("send notify by bkci failed[${res.code}]: ${res.body?.string()}")
            return
        }

        val result = res.body!!.string().readJsonString<Result>()
        if (result.status != 0 || result.data != true) {
            logger.error("send notify by bkci failed: $result")
        }
    }

    private data class Result(
        val status: Int,
        val message: String? = null,
        val data: Boolean? = null
    )

    private fun authorizationStr(credential: BkciChannelCredential): String = with(credential) {
        "{\"bk_app_code\": \"$appCode\", \"bk_app_secret\": \"$appSecret\"}"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkciNotifyClient::class.java)
    }
}
