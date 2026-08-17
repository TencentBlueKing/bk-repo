package com.tencent.bkrepo.preview.model

import com.tencent.bkrepo.preview.pojo.share.ArtifactShareKind
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareResourceType
import com.tencent.bkrepo.preview.pojo.share.ShareVisibility
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("artifact_share")
@CompoundIndexes(
    CompoundIndex(
        name = "project_repo_share_resource_uk",
        def = "{'projectId': 1, 'repoName': 1, 'shareKind': 1, 'resourceType': 1, 'resourceId': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "created_by_mtime_idx",
        def = "{'createdBy': 1, 'shareKind': 1, 'resourceType': 1, 'lastModifiedDate': -1, '_id': -1}",
        background = true,
    ),
    CompoundIndex(
        name = "visibility_mtime_idx",
        def = "{'visibility': 1, 'shareKind': 1, 'resourceType': 1, 'lastModifiedDate': -1, '_id': -1}",
        background = true,
    ),
    CompoundIndex(
        name = "user_ids_mtime_idx",
        def = "{'userIds': 1, 'shareKind': 1, 'resourceType': 1, 'lastModifiedDate': -1, '_id': -1}",
        background = true,
    ),
    CompoundIndex(
        name = "org_ids_mtime_idx",
        def = "{'orgIds': 1, 'shareKind': 1, 'resourceType': 1, 'lastModifiedDate': -1, '_id': -1}",
        background = true,
    ),
    CompoundIndex(
        name = "featured_mtime_idx",
        def = "{'featured': 1, 'shareKind': 1, 'resourceType': 1, 'lastModifiedDate': -1, '_id': -1}",
        background = true,
    ),
)
data class TArtifactShare(
    /**
     * 对外 shareId，即 Mongo `_id`。创建时由 generateShareId() 写入 32 位无横线 UUID。
     */
    @Id
    var id: String? = null,
    var shareKind: ArtifactShareKind = ArtifactShareKind.MATERIAL,
    /**
     * 资源体系：本期固定 [ArtifactShareResourceType.DRIVE_NODE]。
     */
    var resourceType: ArtifactShareResourceType = ArtifactShareResourceType.DRIVE_NODE,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var projectId: String,
    var repoName: String,
    var resourceId: Long,
    var fullPath: String,
    var visibility: ShareVisibility,
    /**
     * CUSTOM 指定用户 ID；PUBLIC 时为空。
     */
    var userIds: List<String> = emptyList(),
    /**
     * CUSTOM 指定组织 ID（库内组织 ID）；PUBLIC 时为空。
     */
    var orgIds: List<String> = emptyList(),
    /**
     * 平台精选。与 visibility 独立，PUBLIC / CUSTOM 都可以被标记为精选。
     */
    var featured: Boolean = false,
    var agentId: String? = null,
    var conversationId: String? = null,
    var artifactName: String? = null,
    /**
     * 作品类型，来自节点元数据 IMATE_ARTIFACT_TYPE。
     */
    var artifactType: String? = null,
    /**
     * 创建/更新分享时签发的预览临时 token（明文存库，对外 API 不返回）。
     */
    var previewToken: String? = null,
    /**
     * 创建/更新分享时签发的下载临时 token（明文存库，对外 API 不返回）。
     */
    var downloadToken: String? = null,
)
