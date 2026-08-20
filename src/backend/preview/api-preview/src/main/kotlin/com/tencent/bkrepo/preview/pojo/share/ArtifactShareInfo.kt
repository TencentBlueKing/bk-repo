package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "作品分享信息")
data class ArtifactShareInfo(
    @get:Schema(title = "分享 ID")
    val shareId: String,
    @get:Schema(title = "分享类型")
    val shareKind: ArtifactShareKind,
    @get:Schema(title = "资源类型（DRIVE_NODE / NODE）")
    val resourceType: ArtifactShareResourceType,
    @get:Schema(title = "项目 ID")
    val projectId: String,
    @get:Schema(title = "仓库名")
    val repoName: String,
    @get:Schema(title = "资源 ID（DRIVE_NODE 为 inode）")
    val resourceId: Long,
    @get:Schema(title = "完整路径")
    val fullPath: String,
    @get:Schema(title = "可见性")
    val visibility: ShareVisibility,
    @get:Schema(title = "指定用户 ID")
    val userIds: List<String> = emptyList(),
    @get:Schema(title = "指定组织展示 ID")
    val orgIds: List<String> = emptyList(),
    @get:Schema(title = "是否平台精选；与 visibility 独立")
    val featured: Boolean = false,
    @get:Schema(title = "Agent ID")
    val agentId: String? = null,
    @get:Schema(title = "会话 ID")
    val conversationId: String? = null,
    @get:Schema(title = "作品名")
    val artifactName: String? = null,
    @get:Schema(title = "作品类型（IMATE_ARTIFACT_TYPE，如 html/image/pdf/video/audio）")
    val type: String? = null,
    @get:Schema(title = "短链路径，如 /a/{shareId}")
    val sharePath: String,
    @get:Schema(title = "创建人")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: LocalDateTime,
    @get:Schema(title = "最近修改时间")
    val lastModifiedDate: LocalDateTime,
)
