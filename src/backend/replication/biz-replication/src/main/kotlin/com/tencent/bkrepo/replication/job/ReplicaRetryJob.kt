package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.cluster.StandaloneJob
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.service.ReplicaFailureRecordService
import com.tencent.bkrepo.replication.service.ReplicaRetryService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 同步失败重试任务
 * 定期扫描失败记录并进行重试
 */
@Component
class ReplicaRetryJob(
    private val replicaFailureRecordService: ReplicaFailureRecordService,
    private val replicaRetryService: ReplicaRetryService,
    private val replicationProperties: ReplicationProperties,
) {

    @StandaloneJob
    @Scheduled(fixedDelay = 3600 * 1000)
    @SchedulerLock(name = "ReplicaRetryJob", lockAtMostFor = "PT168H")
    fun retryFailedRecords() {
        logger.info("Starting replica retry job")

        try {
            val maxRetryNum = replicationProperties.maxRetryNum
            val recordsToRetry = replicaFailureRecordService.getRecordsForRetry(maxRetryNum)

            if (recordsToRetry.isEmpty()) {
                logger.info("No records need to be retried")
                return
            }

            logger.info("Found ${recordsToRetry.size} records to retry")
            val stats = RetryStats()

            recordsToRetry.forEach { record ->
                processRetryRecord(record, maxRetryNum, stats)
            }

            logger.info(
                "Replica retry job completed." +
                    " Success: ${stats.successCount}, Failed: ${stats.failureCount}, Skipped: ${stats.skippedCount}"
            )
            cleanExpiredFailureRecords()
        } catch (e: Exception) {
            logger.error("Error occurred during replica retry job", e)
        }
    }

    private fun processRetryRecord(record: TReplicaFailureRecord, maxRetryNum: Int, stats: RetryStats) {
        logger.info("Retrying record[${record.id}], current retry count: ${record.retryCount}")

        try {
            replicaFailureRecordService.updateRetryStatus(record.id!!, true)
            val retrySuccess = replicaRetryService.retryFailureRecord(record)
            handleRetryResult(record, retrySuccess, stats)
        } catch (e: Exception) {
            logger.error("Error occurred while retrying record[${record.id}]", e)
            replicaFailureRecordService.incrementRetryCount(
                record.id!!, "Exception during retry: ${e.message}"
            )
            stats.failureCount++
        } finally {
            replicaFailureRecordService.updateRetryStatus(record.id!!, false)
        }
    }

    private fun handleRetryResult(record: TReplicaFailureRecord, success: Boolean, stats: RetryStats) {
        if (success) {
            logger.info("Successfully retried record[${record.id}]")
            replicaFailureRecordService.deleteRecord(record.id!!)
            stats.successCount++
        } else {
            logger.warn("Failed to retry record[${record.id}]")
            replicaFailureRecordService.incrementRetryCount(record.id!!, "Retry failed")
            stats.failureCount++
        }
    }

    private class RetryStats {
        var successCount = 0
        var failureCount = 0
        var skippedCount = 0
    }

    /**
     * 清理过期失败记录的任务
     */
    fun cleanExpiredFailureRecords() {
        logger.info("Starting clean expired failure records job")

        // 检查是否启用自动清理
        if (!replicationProperties.autoCleanExpiredFailedRecords) {
            logger.info("Auto clean expired failure records is disabled, skipping")
            return
        }

        try {
            val maxRetryNum = replicationProperties.maxRetryNum
            val retentionDays = replicationProperties.failedRecordRetentionDays

            logger.info("Cleaning expired failure records with maxRetryNum=$maxRetryNum, retentionDays=$retentionDays")

            val cleanedCount = replicaFailureRecordService.cleanExpiredRecords(maxRetryNum, retentionDays)
            logger.info("Clean expired failure records job completed. Cleaned records: $cleanedCount")
        } catch (e: Exception) {
            logger.error("Error occurred during clean expired failure records job", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaRetryJob::class.java)
    }
}
