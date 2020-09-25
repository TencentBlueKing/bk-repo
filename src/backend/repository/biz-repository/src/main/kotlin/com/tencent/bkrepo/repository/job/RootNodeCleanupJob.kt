package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * 清理根节点
 */
@Component
class RootNodeCleanupJob {

    @Autowired
    private lateinit var nodeDao: NodeDao

    fun cleanup() {
        logger.info("Starting to cleanup root node.")
        var deletedCount = 0L
        val startTimeMillis = System.currentTimeMillis()
        val mongoTemplate = nodeDao.determineMongoTemplate()
        val query = Query.query(Criteria.where(TNode::name.name).`in`("", null))
        for (sequence in 0 until SHARDING_COUNT) {
            val collectionName = nodeDao.parseSequenceToCollectionName(sequence)
            val deleteResult = mongoTemplate.remove(query, collectionName)
            deletedCount += deleteResult.deletedCount
        }
        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info(
            "deletedCount: $deletedCount, elapse [$elapseTimeMillis] ms totally."
        )
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
