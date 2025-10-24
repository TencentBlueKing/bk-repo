package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.cluster.StandaloneJob
import com.tencent.bkrepo.replication.service.FederationMetadataTrackingService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 联邦文件传输重试任务
 * 定期扫描未完成的文件传输记录并进行重试
 */
@Component
class FederationFileTransferRetryJob(
    private val federationMetadataTrackingService: FederationMetadataTrackingService,
) {

    @StandaloneJob
    @Scheduled(fixedDelay = 3600 * 1000) // 每30分钟执行一次
    @SchedulerLock(name = "FederationFileTransferRetryJob", lockAtMostFor = "PT168H")
    fun retryPendingFileTransfers() {
        logger.info("Starting federation file transfer retry job")

        try {
            val processedCount = federationMetadataTrackingService.processPendingFileTransfers()
            logger.info("Federation file transfer retry job completed, processed $processedCount files")

            // 清理过期的失败记录
            val cleanedCount = federationMetadataTrackingService.cleanExpiredFailedRecords()
            if (cleanedCount > 0) {
                logger.info("Cleaned $cleanedCount expired failed records")
            }
        } catch (e: Exception) {
            logger.error("Federation file transfer retry job failed", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationFileTransferRetryJob::class.java)
    }
}
