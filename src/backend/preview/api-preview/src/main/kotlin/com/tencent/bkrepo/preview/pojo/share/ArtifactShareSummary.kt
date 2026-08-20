package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "用户有权限访问的作品摘要")
data class ArtifactShareSummary(
    @get:Schema(title = "分享 ID")
    val shareId: String,
    @get:Schema(title = "分享类型")
    val shareKind: ArtifactShareKind,
    @get:Schema(title = "项目 ID")
    val projectId: String,
    @get:Schema(title = "仓库名")
    val repoName: String,
    @get:Schema(title = "资源 ID")
    val resourceId: Long,
    @get:Schema(title = "作品名")
    val artifactName: String? = null,
    @get:Schema(title = "作品类型（IMATE_ARTIFACT_TYPE，如 html/image/pdf/video/audio）")
    val type: String? = null,
    @get:Schema(title = "完整路径，供拼接 Drive 临时下载 URL")
    val fullPath: String,
    @get:Schema(title = "下载临时 token；缺失时为 null")
    val downloadToken: String? = null,
    @get:Schema(title = "创建人")
    val createdBy: String,
    @get:Schema(title = "产出该作品的 Agent ID")
    val agentId: String? = null,
    @get:Schema(title = "是否平台精选；与 visibility 独立")
    val featured: Boolean = false,
    @get:Schema(title = "最近修改时间")
    val lastModifiedDate: LocalDateTime,
)
