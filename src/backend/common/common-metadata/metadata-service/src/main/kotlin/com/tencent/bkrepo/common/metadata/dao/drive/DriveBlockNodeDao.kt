package com.tencent.bkrepo.common.metadata.dao.drive

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.drive.TDriveBlockNode
import com.tencent.bkrepo.common.metadata.util.drive.DriveBlockNodeQueryHelper
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class DriveBlockNodeDao : DriveHashShardingMongoDao<TDriveBlockNode>() {

    fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        ino: Long,
        snapSeq: Long? = null,
    ): List<TDriveBlockNode> {
        val query = DriveBlockNodeQueryHelper.listBlocksQuery(range, projectId, repoName, ino, snapSeq)
        return find(query)
    }
}
