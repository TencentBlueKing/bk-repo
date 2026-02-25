package com.tencent.bkrepo.common.metadata.model

import com.tencent.bkrepo.common.metadata.model.TDriveSnapshot.Companion.REPO_NAME_IDX
import com.tencent.bkrepo.common.metadata.model.TDriveSnapshot.Companion.REPO_NAME_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * Drive 文件系统快照。
 */
@Document("drive_snapshot")
@CompoundIndexes(
    CompoundIndex(name = REPO_NAME_IDX, def = REPO_NAME_IDX_DEF, unique = true, background = true)
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
     * 快照状态
     */
    var status: Int = ACTIVE,
) {
    companion object {
        const val ACTIVE = 0
        const val DELETING = 1
        const val DELETED = 2
        const val REPO_NAME_IDX = "repo_name_idx"
        const val REPO_NAME_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'name': 1}"
    }
}
