package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "批量查询作品分享状态")
data class ArtifactShareBatchStatusRequest(
    @get:Schema(title = "项目 ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名", required = true)
    val repoName: String,
    @get:Schema(title = "资源 ID 列表", required = true)
    val resourceIds: List<Long>,
)
