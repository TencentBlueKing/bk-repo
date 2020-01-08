package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.api.util.LoggerHolder
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.FileReferenceService
import java.time.LocalDateTime
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 清理被标记为删除的node，同时减少文件引用
 * @author: carrypan
 * @date: 2019/12/24
 */
@Component
class DeletedNodeCleanupJob {

    @Autowired
    private lateinit var nodeDao: NodeDao

    @Autowired
    private lateinit var fileReferenceService: FileReferenceService

    @Autowired
    private lateinit var repositoryProperties: RepositoryProperties

    @Scheduled(cron = "0 20 0/1 * * ?")
    @SchedulerLock(name = "DeletedNodeCleanupJob", lockAtMostFor = "PT59M")
    fun cleanUp() {
        logger.info("Starting to clean up deleted nodes.")
        try {
            if (repositoryProperties.deletedNodeReserveDays >= 0) {
                var cleanupCount = 0L
                val startTimeMillis = System.currentTimeMillis()
                val expireDate = LocalDateTime.now().minusDays(repositoryProperties.deletedNodeReserveDays)
                val query = Query.query(Criteria.where(TNode::deleted.name).lt(expireDate))
                for (sequence in 0 until TNode.SHARDING_COUNT) {
                    val collectionName = nodeDao.parseSequenceToCollectionName(sequence)
                    val deletedNodeList = nodeDao.determineMongoTemplate().find(query, TNode::class.java, collectionName)
                    deletedNodeList.forEach {
                        fileReferenceService.decrement(it)
                        nodeDao.determineMongoTemplate().remove(it, collectionName)
                        cleanupCount += 1
                    }
                }
                val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
                logger.info("[$cleanupCount] nodes has been clean up, elapse [$elapseTimeMillis] ms totally.")
            } else {
                logger.info("Reserve days[${repositoryProperties.deletedNodeReserveDays}] for deleted nodes is less than 0, skip cleaning up.")
            }
        } catch (exception: Exception) {
            logger.error("Clean up deleted nodes error.", exception)
        }
    }

    companion object {
        private val logger = LoggerHolder.JOB
    }
}
