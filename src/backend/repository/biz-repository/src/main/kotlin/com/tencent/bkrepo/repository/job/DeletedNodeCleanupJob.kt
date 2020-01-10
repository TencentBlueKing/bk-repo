package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.FileReferenceService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

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
                var totalCleanupCount = 0L
                var fileCleanupCount = 0L
                var folderCleanupCount = 0L
                val startTimeMillis = System.currentTimeMillis()
                val expireDate = LocalDateTime.now().minusDays(repositoryProperties.deletedNodeReserveDays)
                val mongoTemplate = nodeDao.determineMongoTemplate()
                val query = Query.query(Criteria.where(TNode::deleted.name).lt(expireDate))
                for (sequence in 0 until TNode.SHARDING_COUNT) {
                    val collectionName = nodeDao.parseSequenceToCollectionName(sequence)
                    query.with(PageRequest.of(0, 1000))
                    var deletedNodeList = mongoTemplate.find(query, TNode::class.java, collectionName)
                    while(deletedNodeList.isNotEmpty()) {
                        logger.info("Retrieved [${deletedNodeList.size}] deleted records to be clean up.")
                        deletedNodeList.forEach {
                            if(it.folder) {
                                folderCleanupCount += 1
                            } else {
                                fileReferenceService.decrement(it)
                                fileCleanupCount += 1
                            }
                            mongoTemplate.remove(it, collectionName)
                            totalCleanupCount += 1
                        }
                        deletedNodeList = mongoTemplate.find(query, TNode::class.java, collectionName)
                    }
                }
                val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
                logger.info("[$totalCleanupCount] nodes has been clean up, file[$fileCleanupCount], folder[$folderCleanupCount]" +
                    ", elapse [$elapseTimeMillis] ms totally.")
            } else {
                logger.info("Reserve days[${repositoryProperties.deletedNodeReserveDays}] for deleted nodes is less than 0, skip cleaning up.")
            }
        } catch (exception: Exception) {
            logger.error("Clean up deleted nodes failed.", exception)
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
