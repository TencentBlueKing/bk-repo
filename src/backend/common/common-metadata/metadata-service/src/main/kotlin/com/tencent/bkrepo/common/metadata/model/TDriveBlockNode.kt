package com.tencent.bkrepo.common.metadata.model

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKey
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode.Companion.BLOCK_IDX
import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode.Companion.BLOCK_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode.Companion.SHA256_IDX
import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode.Companion.SHA256_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * Drive 文件系统块节点
 *
 * 通过 nodeId 与 TDriveNode 关联，使用 nodeId 分表。
 * 保留 projectId/repoName 用于批量操作和运维查询
 */
@ShardingDocument("drive_block_node")
@CompoundIndexes(
    CompoundIndex(name = BLOCK_IDX, def = BLOCK_IDX_DEF),
    CompoundIndex(name = SHA256_IDX, def = SHA256_IDX_DEF, background = true),
)
data class TDriveBlockNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    val projectId: String,
    val repoName: String,
    @ShardingKey(count = SHARDING_COUNT)
    val nodeId: String,
    val startPos: Long,
    var sha256: String,
    var crc64ecma: String? = null,
    val size: Long,
    val endPos: Long = startPos + size - 1,
    var deleted: LocalDateTime? = null,
) {
    companion object {
        const val BLOCK_IDX = "node_start_pos_idx"
        const val BLOCK_IDX_DEF = "{'nodeId': 1, 'startPos': 1, 'deleted': 1}"
        const val SHA256_IDX = "sha256_idx"
        const val SHA256_IDX_DEF = "{'sha256': 1}"
    }
}
