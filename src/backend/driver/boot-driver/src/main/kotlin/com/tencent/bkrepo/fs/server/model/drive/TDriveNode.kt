package com.tencent.bkrepo.fs.server.model.drive

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKeys
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.INO_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.INO_IDX_DEF
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PARENT_NAME_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PARENT_NAME_IDX_DEF
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PARENT_SNAP_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PARENT_SNAP_IDX_DEF
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PROJECT_REPO_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PROJECT_REPO_IDX_DEF
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PROJECT_REPO_MODIFIED_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.PROJECT_REPO_MODIFIED_IDX_DEF
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TARGET_INO_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TARGET_INO_IDX_DEF
import com.tencent.bkrepo.repository.constant.PROJECT_ID
import com.tencent.bkrepo.repository.constant.REPO_NAME
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * Drive 文件系统 inode 条目
 *
 * 修改非当前快照记录时采用 COW（写时复制）：创建同 ino 的新副本后再修改，并标记旧记录删除，这样可支持快照并避免修改子节点和块节点的关联 ino，
 * 属性字段冗余存储在每条记录中。属性变更时通过 updateMany(ino) 批量同步。
 * 硬链接使用场景极少，以常规操作的单次查询性能为优先。
 */
@ShardingDocument("drive_node")
@CompoundIndexes(
    CompoundIndex(name = INO_IDX, def = INO_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = TARGET_INO_IDX, def = TARGET_INO_IDX_DEF, background = true),
    CompoundIndex(name = PARENT_NAME_IDX, def = PARENT_NAME_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = PARENT_SNAP_IDX, def = PARENT_SNAP_IDX_DEF, background = true),
    CompoundIndex(name = PROJECT_REPO_IDX, def = PROJECT_REPO_IDX_DEF, background = true),
    CompoundIndex(name = PROJECT_REPO_MODIFIED_IDX, def = PROJECT_REPO_MODIFIED_IDX_DEF, background = true),
)
@ShardingKeys(columns = [PROJECT_ID, REPO_NAME], count = SHARDING_COUNT)
data class TDriveNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    /**
     * 修改时间（纳秒时间戳）
     */
    var mtime: Long = 0,

    /**
     * 属性变更时间（纳秒时间戳）
     */
    var ctime: Long = 0,

    /**
     * 访问时间（纳秒时间戳）
     */
    var atime: Long = 0,

    var projectId: String,
    var repoName: String,

    /**
     * 自身 inode 号，在仓库内需要保证唯一避免drive-block-node数据混淆
     */
    var ino: Long,

    /**
     * 硬链接指向的目标Ino，仅后端使用，该字段存在时替代ino返回给客户端
     */
    var targetIno: Long? = null,

    /**
     * 父目录 inode 号，根节点该字段值为null
     */
    var parent: Long? = null,

    /**
     * 文件名
     */
    var name: String,

    /**
     * 文件大小（字节）
     */
    var size: Long,

    /**
     * 文件模式（Linux st_mode，包含文件类型位和权限位）
     */
    var mode: Int,

    /**
     * 文件类型，取值见 TYPE_FILE/TYPE_DIRECTORY/TYPE_SYMLINK
     */
    var type: Int,

    /**
     * 硬链接数
     */
    var nlink: Int,

    /**
     * 用户 ID
     */
    var uid: Int,

    /**
     * 组 ID
     */
    var gid: Int,

    /**
     * 设备 ID（设备文件用）
     */
    var rdev: Int,

    /**
     * 文件标志
     */
    var flags: Int,

    /**
     * 删除时间
     */
    var deleted: LocalDateTime? = null,

    /**
     * 软链接目标路径（仅 S_IFLNK 时有值）
     */
    var symlinkTarget: String? = null,

    /**
     * 创建时的快照序列号
     */
    var snapSeq: Long = 0,

    /**
     * 节点删除时的快照序列号，Long.MAX_VALUE 表示未删除
     */
    var deleteSnapSeq: Long = Long.MAX_VALUE,
) {

    /**
     * 获取实际Ino，由于ino必须设置唯一索引避免重复导致block数据混乱，但是硬链接与普通node的ino不能重复，因此硬链接的ino为占位无实际作用，
     * 需要使用targetIno访问
     */
    fun realIno() = targetIno ?: ino

    companion object {
        const val TYPE_FILE = 1
        const val TYPE_DIRECTORY = 2
        const val TYPE_SYMLINK = 3
        val ALLOWED_TYPES = setOf(TYPE_FILE, TYPE_DIRECTORY, TYPE_SYMLINK)
        const val INO_IDX = "ino_idx"
        const val INO_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'ino': 1, 'deleted': 1}"
        const val TARGET_INO_IDX = "target_ino_idx"
        const val TARGET_INO_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'targetIno': 1}"
        const val PARENT_NAME_IDX = "parent_name_idx"
        const val PARENT_NAME_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'parent': 1, 'name': 1, 'deleted': 1}"
        const val PARENT_SNAP_IDX = "parent_snap_idx"
        const val PARENT_SNAP_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'parent': 1, 'deleteSnapSeq': 1, 'snapSeq': 1}"
        const val PROJECT_REPO_IDX = "project_repo_idx"
        const val PROJECT_REPO_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'deleteSnapSeq': 1, 'snapSeq': 1}"
        const val PROJECT_REPO_MODIFIED_IDX = "project_repo_modified_idx"
        const val PROJECT_REPO_MODIFIED_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'lastModifiedDate': 1}"
    }
}
