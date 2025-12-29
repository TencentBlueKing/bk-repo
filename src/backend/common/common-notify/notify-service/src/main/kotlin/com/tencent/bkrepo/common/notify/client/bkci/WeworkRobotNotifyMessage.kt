package com.tencent.bkrepo.common.notify.client.bkci

import com.tencent.bkrepo.common.notify.api.bkci.WeworkReceiverType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "企业微信机器人消息")
data class WeworkRobotNotifyMessage(
    @get:Schema(title = "接收人Id", required = true)
    val receivers: String,
    @get:Schema(title = "接收人类型", required = true)
    val receiverType: WeworkReceiverType,
    @get:Schema(title = "文本内容类型", required = true)
    var textType: WeworkTextType,
    @get:Schema(title = "文本内容", required = true)
    var message: String,
    @get:Schema(title = "attachments消息事件", required = false)
    var attachments: WeworkMarkdownAttachment? = null
)
