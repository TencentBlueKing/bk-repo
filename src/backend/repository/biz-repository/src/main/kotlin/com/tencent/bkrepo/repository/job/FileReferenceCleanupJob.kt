package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.dao.FileReferenceDao
import com.tencent.bkrepo.repository.model.TFileReference
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 清理引用=0的文件
 * @author: carrypan
 * @date: 2019/12/24
 */
@Component
class FileReferenceCleanupJob {

    @Autowired
    private lateinit var fileReferenceDao: FileReferenceDao

    @Autowired
    private lateinit var storageService: StorageService

    @Scheduled(cron = "0 0 2/3 * * ?")
    @SchedulerLock(name = "FileReferenceCleanupJob", lockAtMostFor = "PT1H")
    fun cleanUp() {
        logger.info("Starting to clean up file reference.")
        var totalCount = 0L
        var cleanupCount = 0L
        var failedCount = 0L
        var fileMissingCount = 0L
        val startTimeMillis = System.currentTimeMillis()
        val query = Query.query(Criteria.where(TFileReference::count.name).`is`(0))
        for (sequence in 0 until SHARDING_COUNT) {
            val collectionName = fileReferenceDao.parseSequenceToCollectionName(sequence)
            val zeroReferenceList = fileReferenceDao.determineMongoTemplate().find(query, TFileReference::class.java, collectionName)
            zeroReferenceList.forEach {
                val storageCredentials = it.storageCredentials?.let { value -> objectMapper.readValue(value, StorageCredentials::class.java) }
                try {
                    if (it.sha256.isNotBlank() && storageService.exist(it.sha256, storageCredentials)) {
                        storageService.delete(it.sha256, storageCredentials)
                        cleanupCount += 1
                    } else {
                        logger.warn("File[${it.sha256}] is missing on [$storageCredentials], skip cleaning up.")
                        fileMissingCount += 1
                    }
                    fileReferenceDao.determineMongoTemplate().remove(it, collectionName)
                } catch (exception: Exception) {
                    logger.error("Failed to delete file[${it.sha256}] on [$storageCredentials].", exception)
                    failedCount += 1
                }
                totalCount += 1
            }
        }
        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info("Clean up [$totalCount] files with zero reference, success[$cleanupCount], failed[$failedCount], " +
            "file missing[$fileMissingCount], elapse [$elapseTimeMillis] ms totally.")
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
