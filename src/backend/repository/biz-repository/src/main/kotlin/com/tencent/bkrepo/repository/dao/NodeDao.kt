package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingMongoDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.springframework.stereotype.Repository

/**
 * 节点 Dao
 */
@Repository
class NodeDao : ShardingMongoDao<TNode>() {
    /**
     * 查询节点model
     */
    fun findNode(projectId: String, repoName: String, fullPath: String): TNode? {
        return this.findOne(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
    }
}
