package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "打开作品分享返回的访问地址")
data class ArtifactShareOpenInfo(
    @get:Schema(title = "分享信息")
    val share: ArtifactShareInfo,
    @get:Schema(title = "预览 URL")
    val previewUrl: String,
    @get:Schema(title = "下载 URL")
    val downloadUrl: String,
)
