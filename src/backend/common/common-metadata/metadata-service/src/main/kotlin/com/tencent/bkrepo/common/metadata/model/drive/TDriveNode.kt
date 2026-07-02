package com.tencent.bkrepo.common.metadata.model.drive

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKeys
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.INO_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.INO_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.METADATA_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.METADATA_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PARENT_NAME_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PARENT_NAME_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PARENT_SNAP_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PARENT_SNAP_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PROJECT_REPO_DELETED_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PROJECT_REPO_DELETED_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PROJECT_REPO_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PROJECT_REPO_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PROJECT_REPO_MODIFIED_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.PROJECT_REPO_MODIFIED_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.REAL_INO_IDX
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.REAL_INO_IDX_DEF
import com.tencent.bkrepo.repository.constant.PROJECT_ID
import com.tencent.bkrepo.repository.constant.REPO_NAME
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

@ShardingDocument("drive_node")
@CompoundIndexes(
    CompoundIndex(name = INO_IDX, def = INO_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = REAL_INO_IDX, def = REAL_INO_IDX_DEF, background = true),
    CompoundIndex(name = PARENT_NAME_IDX, def = PARENT_NAME_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = PARENT_SNAP_IDX, def = PARENT_SNAP_IDX_DEF, background = true),
    CompoundIndex(name = PROJECT_REPO_IDX, def = PROJECT_REPO_IDX_DEF, background = true),
    CompoundIndex(name = PROJECT_REPO_MODIFIED_IDX, def = PROJECT_REPO_MODIFIED_IDX_DEF, background = true),
    CompoundIndex(name = PROJECT_REPO_DELETED_IDX, def = PROJECT_REPO_DELETED_IDX_DEF, background = true),
    CompoundIndex(name = METADATA_IDX, def = METADATA_IDX_DEF, background = true),
)
@ShardingKeys(columns = [PROJECT_ID, REPO_NAME], count = SHARDING_COUNT)
data class TDriveNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedClientId: String? = null,
    var lastModifiedDate: LocalDateTime,
    var mtime: Long = 0,
    var ctime: Long = 0,
    var atime: Long = 0,
    var projectId: String,
    var repoName: String,
    var ino: Long,
    var targetIno: Long? = null,
    var realIno: Long = targetIno ?: ino,
    var parent: Long? = null,
    var name: String,
    var size: Long,
    var mode: Int,
    var type: Int,
    var nlink: Int,
    var uid: Int,
    var gid: Int,
    var rdev: Int,
    var flags: Int,
    var deleted: LocalDateTime? = null,
    var symlinkTarget: String? = null,
    var snapSeq: Long = 0,
    var deleteSnapSeq: Long = Long.MAX_VALUE,

    /**
     * 节点元数据
     */
    var metadata: MutableList<TMetadata>? = null,
) {
    companion object {
        const val TYPE_FILE = 1
        const val TYPE_DIRECTORY = 2
        const val TYPE_SYMLINK = 3
        val ALLOWED_TYPES = setOf(TYPE_FILE, TYPE_DIRECTORY, TYPE_SYMLINK)
        const val INO_IDX = "ino_idx"
        const val INO_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'ino': 1, 'deleted': 1}"
        const val REAL_INO_IDX = "real_ino_idx"
        const val REAL_INO_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'realIno': 1}"
        const val PARENT_NAME_IDX = "parent_name_idx"
        const val PARENT_NAME_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'parent': 1, 'name': 1, 'deleted': 1}"
        const val PARENT_SNAP_IDX = "parent_snap_idx"
        const val PARENT_SNAP_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'parent': 1, 'deleteSnapSeq': 1, 'snapSeq': 1}"
        const val PROJECT_REPO_IDX = "project_repo_idx"
        const val PROJECT_REPO_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'deleteSnapSeq': 1, 'snapSeq': 1}"
        const val PROJECT_REPO_MODIFIED_IDX = "project_repo_modified_idx"
        const val PROJECT_REPO_MODIFIED_IDX_DEF =
            "{'projectId': 1, 'repoName': 1, 'lastModifiedDate': 1, 'lastModifiedClientId': 1}"
        const val PROJECT_REPO_DELETED_IDX = "project_repo_deleted_idx"
        const val PROJECT_REPO_DELETED_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'deleted': 1}"
        const val METADATA_IDX = "metadata_idx"
        const val METADATA_IDX_DEF = "{'metadata.key': 1, 'metadata.value': 1}"
    }
}
