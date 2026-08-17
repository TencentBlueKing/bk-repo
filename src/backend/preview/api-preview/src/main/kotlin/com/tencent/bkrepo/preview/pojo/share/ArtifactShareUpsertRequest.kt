package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "创建或更新作品分享")
data class ArtifactShareUpsertRequest(
    @get:Schema(title = "项目 ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名", required = true)
    val repoName: String,
    @get:Schema(title = "资源 ID（DRIVE_NODE 为 inode）", required = true)
    val resourceId: Long,
    @get:Schema(title = "可见性", required = true)
    val visibility: ShareVisibility,
    @get:Schema(title = "指定用户 ID；PUBLIC 时忽略并清空")
    val userIds: List<String> = emptyList(),
    @get:Schema(title = "指定组织展示 ID；PUBLIC 时忽略并清空")
    val orgIds: List<String> = emptyList(),
)
