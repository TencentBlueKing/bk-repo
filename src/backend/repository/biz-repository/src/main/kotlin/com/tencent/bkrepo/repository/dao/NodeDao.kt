package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingMongoDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 节点 Dao
 */
@Repository
class NodeDao : ShardingMongoDao<TNode>() {
    /**
     * 查询节点
     */
    fun findNode(projectId: String, repoName: String, fullPath: String): TNode? {
        if (PathUtils.isRoot(fullPath)) {
            return buildRootNode(projectId, repoName)
        }
        return this.findOne(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
    }

    /**
     * 查询节点是否存在
     */
    fun exists(projectId: String, repoName: String, fullPath: String): Boolean {
        if (PathUtils.isRoot(fullPath)) {
            return true
        }
        return this.exists(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
    }

    companion object {
        private fun buildRootNode(projectId: String, repoName: String): TNode {
            return TNode(
                createdBy = StringPool.EMPTY,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = StringPool.EMPTY,
                lastModifiedDate = LocalDateTime.now(),
                projectId = projectId,
                repoName = repoName,
                folder = true,
                path = PathUtils.ROOT,
                name = StringPool.EMPTY,
                fullPath = PathUtils.ROOT,
                size = 0
            )
        }
    }
}
