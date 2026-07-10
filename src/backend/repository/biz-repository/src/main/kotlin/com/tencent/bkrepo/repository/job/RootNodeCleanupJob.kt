package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.metadata.routing.NodeShardReadSupport
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component

/**
 * 清理根节点
 */
@Component
class RootNodeCleanupJob(
    private val nodeShardReadSupport: NodeShardReadSupport,
) {

    fun cleanup() {
        logger.info("Starting to cleanup root node.")
        var deletedCount = 0L
        val startTimeMillis = System.currentTimeMillis()
        val query = Query(where(TNode::name).inValues("", null))
        deletedCount = nodeShardReadSupport.removeOnShards(SHARDING_COUNT, "node_", query).deletedCount
        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info("deletedCount: $deletedCount, elapse [$elapseTimeMillis] ms totally.")
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
