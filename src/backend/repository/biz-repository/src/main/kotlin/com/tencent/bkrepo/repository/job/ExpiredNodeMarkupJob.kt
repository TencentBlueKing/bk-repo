package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.NodeService
import java.time.LocalDateTime
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 标记已过期的节点为已删除
 * @author: carrypan
 * @date: 2019/12/24
 */
@Component
class ExpiredNodeMarkupJob {

    @Autowired
    private lateinit var nodeDao: NodeDao

    @Autowired
    private lateinit var nodeService: NodeService

    @Scheduled(cron = "0 0 0/3 * * ?")
    @SchedulerLock(name = "ExpiredNodeMarkupJob", lockAtMostFor = "PT1H")
    fun markUp() {
        logger.info("Starting to mark up expired nodes.")
        try {
            var markupCount = 0L
            val startTimeMillis = System.currentTimeMillis()
            val query = Query.query(Criteria.where(TNode::expireDate.name).lt(LocalDateTime.now()))
            val mongoTemplate = nodeDao.determineMongoTemplate()
            for (sequence in 0 until TNode.SHARDING_COUNT) {
                val collectionName = nodeDao.parseSequenceToCollectionName(sequence)
                var page = 0
                query.with(PageRequest.of(page, 1000))
                var deletedNodeList = mongoTemplate.find(query, TNode::class.java, collectionName)
                while (deletedNodeList.isNotEmpty()) {
                    logger.info("Retrieved [${deletedNodeList.size}] expired records to be clean up.")
                    deletedNodeList.forEach {
                        nodeService.deleteByPath(it.projectId, it.repoName, it.fullPath, it.lastModifiedBy)
                        markupCount += 1
                    }
                    page += 1
                    query.with(PageRequest.of(page, 1000))
                    deletedNodeList = mongoTemplate.find(query, TNode::class.java, collectionName)
                }
            }
            val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
            logger.info("[$markupCount] nodes has been marked up with deleted status, elapse [$elapseTimeMillis] ms totally.")
        } catch (exception: Exception) {
            logger.error("Mark up deleted expired error.", exception)
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
