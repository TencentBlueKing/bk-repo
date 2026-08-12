package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "作品分享状态摘要")
data class ArtifactShareStatusItem(
    @get:Schema(title = "资源 ID")
    val resourceId: Long,
    @get:Schema(title = "是否已分享")
    val shared: Boolean,
    @get:Schema(title = "分享 ID")
    val shareId: String? = null,
)
