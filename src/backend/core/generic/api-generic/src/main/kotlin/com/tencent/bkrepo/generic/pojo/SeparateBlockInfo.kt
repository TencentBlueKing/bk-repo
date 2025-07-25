package com.tencent.bkrepo.generic.pojo

import io.swagger.v3.oas.annotations.media.Schema



@Schema(title = "新分块信息")
data class SeparateBlockInfo(
    @get:Schema(title = "分块大小")
    val size: Long,
    @get:Schema(title = "分块sha256")
    val sha256: String,
    @get:Schema(title = "分块crc64ecma")
    val crc64ecma: String?,
    @get:Schema(title = "分块起始位置")
    val startPos: Long,
    @get:Schema(title = "分块uploadID")
    val uploadId: String?
)
