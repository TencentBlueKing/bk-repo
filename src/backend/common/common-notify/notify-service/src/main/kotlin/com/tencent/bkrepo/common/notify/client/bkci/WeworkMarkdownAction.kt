package com.tencent.bkrepo.common.notify.client.bkci

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "企业微信机器人markdown动作")
data class WeworkMarkdownAction(
    @get:Schema(title = "按钮名字", required = true)
    val name: String,
    @get:Schema(title = "按钮文案", required = true)
    val text: String,
    @get:Schema(title = "动作类型", required = true)
    val type: String,
    @get:Schema(title = "按钮值", required = true)
    val value: String,
    @get:Schema(title = "按钮点击后显示值", required = true)
    @field:JsonProperty("replace_text")
    val replaceText: String,
    @get:Schema(title = "按钮边框颜色", required = true)
    @field:JsonProperty("border_color")
    val borderColor: String,
    @get:Schema(title = "按钮文本颜色", required = true)
    @field:JsonProperty("text_color")
    val textColor: String
)
