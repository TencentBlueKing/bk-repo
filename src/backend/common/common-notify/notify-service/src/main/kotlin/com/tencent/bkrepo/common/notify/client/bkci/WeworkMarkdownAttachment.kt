package com.tencent.bkrepo.common.notify.client.bkci

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "企业微信机器人attachment事件消息")
data class WeworkMarkdownAttachment(
    @get:Schema(title = "回调id", required = true)
    @field:JsonProperty("callback_id")
    val callbackId: String,
    @get:Schema(title = "动作集合", required = true)
    val actions: List<WeworkMarkdownAction>
)
