package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.routing.NodeShardReadSupport
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

/**
 * deleted = null -> deleted = 0 的文件
 */
@Component
class NodeDeletedCorrectionJob(
    private val nodeDao: NodeDao,
    @Autowired(required = false) private val nodeShardReadSupport: NodeShardReadSupport? = null,
) {

    fun correct() {
        logger.info("Starting to correct node deleted value.")
        var matchedCount = 0L
        var modifiedCount = 0L
        val startTimeMillis = System.currentTimeMillis()
        val query = Query.query(Criteria.where(TNode::deleted.name).`is`(null))
        val update = Update.update(TNode::deleted.name, 0)
        if (nodeShardReadSupport != null) {
            val result = nodeShardReadSupport.updateMultiOnShards(SHARDING_COUNT, "node_", query, update)
            matchedCount = result.matchedCount
            modifiedCount = result.modifiedCount
        } else {
            val mongoTemplate = nodeDao.determineMongoTemplate()
            for (sequence in 0 until SHARDING_COUNT) {
                val collectionName = nodeDao.parseSequenceToCollectionName(sequence)
                val updateResult = mongoTemplate.updateMulti(query, update, collectionName)
                matchedCount += updateResult.matchedCount
                modifiedCount += updateResult.modifiedCount
            }
        }
        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info(
            "matchedCount: $matchedCount, modifiedCount: $modifiedCount, elapse [$elapseTimeMillis] ms totally."
        )
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
