package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "作品分享游标分页结果")
data class ArtifactSharePage<T>(
    @get:Schema(title = "当前页记录")
    val records: List<T>,
    @get:Schema(title = "下一页游标，无更多时为 null")
    val nextCursor: String? = null,
    @get:Schema(title = "本次请求的页大小")
    val limit: Int,
)
