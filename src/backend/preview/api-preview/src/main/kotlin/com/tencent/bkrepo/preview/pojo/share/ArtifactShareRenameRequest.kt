package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "重命名作品分享")
data class ArtifactShareRenameRequest(
    @get:Schema(title = "站点/作品名称", required = true)
    val artifactName: String,
)
