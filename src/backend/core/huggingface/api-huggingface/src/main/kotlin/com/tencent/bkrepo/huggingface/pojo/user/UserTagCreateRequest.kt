package com.tencent.bkrepo.huggingface.pojo.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "创建 Tag 请求")
data class UserTagCreateRequest(
    @get:Schema(title = "Tag 名称", required = true)
    val tag: String,
    @get:Schema(title = "Tag 消息", required = false)
    val message: String? = null
)

