package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.cluster.StandaloneJob
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.service.EventRecordService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 事件记录重试定时任务
 * 定期扫描未完成的事件记录并进行重试，重试成功后删除记录
 */
@Component
class EventRecordRetryJob(
    private val eventRecordService: EventRecordService,
    private val replicationProperties: ReplicationProperties
) {

    @StandaloneJob
    @Scheduled(fixedDelay = 3600 * 1000) // 每小时执行一次
    @SchedulerLock(name = "EventRecordRetryJob", lockAtMostFor = "PT168H")
    fun retryIncompleteEventRecords() {
        logger.info("Starting event record retry job")

        try {
            val recordsToRetry = getRecordsToRetry()
            if (recordsToRetry.isEmpty()) {
                logger.info("No event records need to be retried")
                return
            }

            logger.info("Found ${recordsToRetry.size} event records to retry")

            val (successCount, failureCount) = processRecords(recordsToRetry)

            logger.info(
                "Event record retry job completed. " +
                    "Total: ${recordsToRetry.size}, Success: $successCount, Failed: $failureCount"
            )
        } catch (e: Exception) {
            logger.warn("Event record retry job failed", e)
        }
    }

    private fun getRecordsToRetry(): List<TEventRecord> {
        val maxRetryNum = replicationProperties.maxRetryNum
        val retryBeforeTime = LocalDateTime.now().minus(replicationProperties.eventRecordRetryInterval)
        return eventRecordService.getRecordsForRetry(maxRetryNum, retryBeforeTime)
    }

    private fun processRecords(recordsToRetry: List<TEventRecord>): Pair<Int, Int> {
        var successCount = 0
        var failureCount = 0

        recordsToRetry.forEach { eventRecord ->
            val success = processSingleRecord(eventRecord)
            if (success) {
                successCount++
            } else {
                failureCount++
            }
        }

        return Pair(successCount, failureCount)
    }

    private fun processSingleRecord(eventRecord: TEventRecord): Boolean {
        return try {
            logger.info(
                "Retrying event record[${eventRecord.id}], current retry count: ${eventRecord.retryCount}"
            )

            // 设置重试状态为 true
            eventRecordService.updateRetryStatus(eventRecord.id!!, true)

            // 重试事件记录
            val success = eventRecordService.retryEventRecord(eventRecord)
            if (success) {
                // 如果触发成功，任务执行完成后会在 updateEventRecordAfterTaskCompletion 中处理重试次数和状态
                true
            } else {
                // 触发重试失败，增加重试次数并重置重试状态
                eventRecordService.incrementRetryCount(eventRecord.id!!)
                logger.info("Failed to trigger retry for eventId: ${eventRecord.id}")
                false
            }
        } catch (e: Exception) {
            // 发生异常，增加重试次数并重置重试状态
            eventRecordService.incrementRetryCount(eventRecord.id!!)
            logger.warn("Error occurred while retrying event record[${eventRecord.id}]", e)
            false
        }
        // 注意：如果触发成功，retrying 状态会在任务执行完成后由 updateEventRecordAfterTaskCompletion 重置
        // 如果触发失败，incrementRetryCount 已经重置了 retrying 状态
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventRecordRetryJob::class.java)
    }
}

