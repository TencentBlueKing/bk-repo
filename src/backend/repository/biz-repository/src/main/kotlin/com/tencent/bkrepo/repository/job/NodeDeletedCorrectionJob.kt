package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

/**
 * deleted = null -> deleted = 0 的文件
 */
@Component
class NodeDeletedCorrectionJob {

    @Autowired
    private lateinit var nodeDao: NodeDao

    fun correct() {
        logger.info("Starting to correct node deleted value.")
        var matchedCount = 0L
        var modifiedCount = 0L
        val startTimeMillis = System.currentTimeMillis()
        val mongoTemplate = nodeDao.determineMongoTemplate()
        val query = Query.query(Criteria.where(TNode::deleted.name).`is`(null))
        val update = Update.update(TNode::deleted.name, 0)
        val collectionName = "node_test"
        val updateResult = mongoTemplate.updateMulti(query, update, collectionName)
        matchedCount += updateResult.matchedCount
        modifiedCount += updateResult.modifiedCount
        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info(
            "matchedCount: $matchedCount, modifiedCount: $modifiedCount, elapse [$elapseTimeMillis] ms totally."
        )
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
