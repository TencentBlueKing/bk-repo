package com.tencent.bkrepo.fs.server.model.drive

import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapshot.Companion.SNAP_SEQ_IDX
import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapshot.Companion.SNAP_SEQ_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * Drive 文件系统快照。
 */
@Document("drive_snapshot")
@CompoundIndexes(
    CompoundIndex(name = SNAP_SEQ_IDX, def = SNAP_SEQ_IDX_DEF, unique = true, background = true)
)
data class TDriveSnapshot(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var projectId: String,
    var repoName: String,

    /**
     * 快照名称
     */
    var name: String,

    /**
     * 快照描述
     */
    var description: String? = null,

    /**
     * 快照对应的快照序列号
     */
    var snapSeq: Long,

    /**
     * 删除时间
     */
    var deleted: LocalDateTime? = null,
) {
    companion object {
        const val SNAP_SEQ_IDX = "repo_name_idx"
        const val SNAP_SEQ_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'snapSeq': 1}"
    }
}
