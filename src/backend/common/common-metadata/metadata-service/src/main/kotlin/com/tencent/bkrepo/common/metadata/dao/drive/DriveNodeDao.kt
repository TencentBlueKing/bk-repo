package com.tencent.bkrepo.common.metadata.dao.drive

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodeDaoHelper
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class DriveNodeDao : DriveHashShardingMongoDao<TDriveNode>() {

    fun findCurrentNode(projectId: String, repoName: String, parent: Long, name: String): TDriveNode? {
        val query = Query(DriveNodeDaoHelper.currentParentNameCriteria(projectId, repoName, parent, name))
        return findOne(query)
    }

    fun findCurrentByIno(projectId: String, repoName: String, ino: Long): TDriveNode? {
        val query = Query(
            where(TDriveNode::projectId).isEqualTo(projectId)
                .and(TDriveNode::repoName.name).isEqualTo(repoName)
                .and(TDriveNode::ino.name).isEqualTo(ino)
                .and(TDriveNode::deleteSnapSeq.name).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted.name).isEqualTo(null),
        )
        return findOne(query)
    }
}
