package com.tencent.bkrepo.repository.job

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.dao.FileReferenceDao
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TFileReference
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.repository.RepoRepository
import com.tencent.bkrepo.repository.service.FileReferenceService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import kotlin.concurrent.thread

/**
 * 重新计算文件索引数量
 * @author: carrypan
 * @date: 2020/01/20
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class FileReferenceRecalculateJob : ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private lateinit var nodeDao: NodeDao

    @Autowired
    private lateinit var repoRepository: RepoRepository

    @Autowired
    private lateinit var fileReferenceDao: FileReferenceDao

    @Autowired
    private lateinit var fileReferenceService: FileReferenceService

    @SchedulerLock(name = "FileReferenceRecalculateJob", lockAtLeastFor = "PT10M", lockAtMostFor = "P1D")
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        thread { recalculate() }
    }

    fun recalculate() {
        logger.info("Starting to recalculate file reference.")
        // 清理file reference
        val startTimeMillis = System.currentTimeMillis()
        for (sequence in 0 until TFileReference.SHARDING_COUNT) {
            val collectionName = fileReferenceDao.parseSequenceToCollectionName(sequence)
            val deleteResult = fileReferenceDao.determineMongoTemplate().getCollection(collectionName).deleteMany(BasicDBObject())
            logger.info("Cleanup file reference collection[$sequence] success: ${deleteResult.deletedCount} records.")
        }
        repoRepository.findAll().forEach { repo ->
            logger.info("Recalculate file reference for [${repo.projectId}/${repo.name}].")
            var page = 0
            val query = Query.query(Criteria.where(TNode::projectId.name).`is`(repo.projectId)
                .and(TNode::repoName.name).`is`(repo.name)
                .and(TNode::folder.name).`is`(false)
            ).with(PageRequest.of(page, 5000))
            var nodeList = nodeDao.find(query)
            while (nodeList.isNotEmpty()) {
                logger.info("Retrieved [${nodeList.size}] records to calculate file reference.")
                nodeList.forEach { node ->
                    if(!fileReferenceService.increment(node, repo)) {
                        logger.warn("Failed to increment file reference of node [$node].")
                    }
                }
                page += 1
                query.with(PageRequest.of(page, 10000))
                nodeList = nodeDao.find(query)
            }
        }

        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info("Recalculate file reference success, elapse [$elapseTimeMillis] ms totally.")
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
