package com.tencent.bkrepo.common.metadata.dao.drive

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodeDaoHelper
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class DriveNodeDao : DriveHashShardingMongoDao<TDriveNode>() {

    fun findCurrentNode(projectId: String, repoName: String, parent: Long, name: String): TDriveNode? {
        val query = Query(DriveNodeDaoHelper.currentParentNameCriteria(projectId, repoName, parent, name))
        return findOne(query)
    }
}
