package com.tencent.bkrepo.common.metadata.model

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKeys
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.model.TDriveNode.Companion.INO_IDX
import com.tencent.bkrepo.common.metadata.model.TDriveNode.Companion.INO_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TDriveNode.Companion.PARENT_NAME_IDX
import com.tencent.bkrepo.common.metadata.model.TDriveNode.Companion.PARENT_NAME_IDX_DEF
import com.tencent.bkrepo.repository.constant.PROJECT_ID
import com.tencent.bkrepo.repository.constant.REPO_NAME
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * Drive 文件系统 inode 条目
 *
 * 硬链接策略：单表冗余存储。同一 ino 可对应多条记录（不同 parent + name），
 * 属性字段冗余存储在每条记录中。属性变更时通过 updateMany(ino) 批量同步。
 * 硬链接使用场景极少，以常规操作的单次查询性能为优先。
 */
@ShardingDocument("drive_node")
@CompoundIndexes(
    CompoundIndex(name = INO_IDX, def = INO_IDX_DEF, background = true),
    CompoundIndex(name = PARENT_NAME_IDX, def = PARENT_NAME_IDX_DEF, unique = true, background = true),
)
@ShardingKeys(columns = [PROJECT_ID, REPO_NAME], count = SHARDING_COUNT)
data class TDriveNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var lastAccessDate: LocalDateTime? = null,

    var projectId: String,
    var repoName: String,

    /**
     * 父目录 inode 号
     */
    var parentId: String,

    /**
     * 文件名
     */
    var name: String,

    /**
     * 文件大小（字节）
     */
    var size: Long,

    /**
     * 文件模式和类型 (S_IFREG, S_IFDIR, S_IFLNK)
     */
    var mode: Int,

    /**
     * 文件类型，文件/目录/符号链接
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
     * 删除时间戳（Unix 秒）
     */
    var deletedAt: Long? = null,

    /**
     * 软链接目标路径（仅 S_IFLNK 时有值）
     */
    var symlinkTarget: String? = null,
) {
    companion object {
        const val INO_IDX = "ino_idx"
        const val INO_IDX_DEF = "{'ino': 1}"
        const val PARENT_NAME_IDX = "parent_name_idx"
        const val PARENT_NAME_IDX_DEF = "{'parent': 1, 'name': 1, 'deletedAt': 1}"
    }
}
