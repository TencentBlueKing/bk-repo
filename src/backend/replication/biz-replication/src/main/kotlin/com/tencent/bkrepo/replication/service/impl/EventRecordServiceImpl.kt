package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.replication.dao.EventRecordDao
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.pojo.event.EventRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.event.EventRecordListOption
import com.tencent.bkrepo.replication.pojo.event.EventRecordRetryRequest
import com.tencent.bkrepo.replication.replica.executor.EventConsumerThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.FederationThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.type.event.EventBasedReplicaJobExecutor
import com.tencent.bkrepo.replication.replica.type.federation.FederationEventBasedReplicaJobExecutor
import com.tencent.bkrepo.replication.service.EventRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 事件记录服务实现类
 */
@Service
class EventRecordServiceImpl(
    private val eventRecordDao: EventRecordDao,
    private val replicaTaskService: ReplicaTaskService,
    private val eventBasedReplicaJobExecutor: EventBasedReplicaJobExecutor,
    private val federationEventBasedReplicaJobExecutor: FederationEventBasedReplicaJobExecutor
) : EventRecordService {

    private val executors = EventConsumerThreadPoolExecutor.instance
    private val federationExecutors = FederationThreadPoolExecutor.instance

    override fun listPage(option: EventRecordListOption): Page<TEventRecord> {
        val (records, total) = eventRecordDao.findPage(
            pageNumber = option.pageNumber,
            pageSize = option.pageSize,
            eventType = option.eventType,
            taskCompleted = option.taskCompleted,
            taskSucceeded = option.taskSucceeded,
            taskKey = option.taskKey,
            sortField = option.sortField,
            sortDirection = option.sortDirection
        )
        return Page(
            pageNumber = option.pageNumber,
            pageSize = option.pageSize,
            totalRecords = total,
            records = records
        )
    }

    override fun findByEventId(eventId: String): TEventRecord? {
        return eventRecordDao.findByEventId(eventId)
    }

    override fun retryEventRecord(request: EventRecordRetryRequest): Boolean {
        return when {
            !request.eventId.isNullOrBlank() -> {
                val record = eventRecordDao.findByEventId(request.eventId!!) ?: return false
                retryEventRecord(record)
            }

            !request.taskKey.isNullOrBlank() -> {
                retryByTaskKey(eventRecordDao.findByTaskKey(request.taskKey!!))
                true
            }

            else -> false
        }

    }

    override fun retryEventRecord(eventRecord: TEventRecord): Boolean {
        return try {
            val event = eventRecord.event
            val eventId = eventRecord.id
            val eventType = eventRecord.eventType
            val taskKey = eventRecord.taskKey

            logger.info(
                "Retrying event record: eventId=$eventId, eventType=$eventType, taskKey=$taskKey, " +
                    "taskCompleted=${eventRecord.taskCompleted}, taskSucceeded=${eventRecord.taskSucceeded}, " +
                    "retryCount=${eventRecord.retryCount}"
            )

            // 执行任务
            when (eventType) {
                "FEDERATION" -> {
                    federationExecutors.execute(
                        Runnable {
                            val task = replicaTaskService.getDetailByTaskKey(taskKey)
                            federationEventBasedReplicaJobExecutor.execute(task, event, eventId)
                        }.trace()
                    )
                }

                else -> {
                    executors.execute(
                        Runnable {
                            val task = replicaTaskService.getDetailByTaskKey(taskKey)
                            eventBasedReplicaJobExecutor.execute(task, event, eventId)
                        }.trace()
                    )
                }
            }
            true
        } catch (e: Exception) {
            logger.warn("Failed to retry event record: ${eventRecord.id}", e)
            false
        }
    }

    override fun getRecordsForRetry(maxRetryTimes: Int, beforeTime: LocalDateTime): List<TEventRecord> {
        return eventRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes, beforeTime)
    }

    override fun updateRetryStatus(recordId: String, retrying: Boolean) {
        eventRecordDao.updateRetryStatus(recordId, retrying)
    }

    override fun incrementRetryCount(recordId: String) {
        eventRecordDao.incrementRetryCount(recordId)
    }

    override fun deleteEventRecord(request: EventRecordDeleteRequest): Boolean {
        return try {
            when {
                !request.eventId.isNullOrBlank() -> {
                    eventRecordDao.deleteByEventId(request.eventId!!)
                    logger.info("Deleted event record by eventId: ${request.eventId}")
                    true
                }

                !request.taskKey.isNullOrBlank() -> {
                    eventRecordDao.deleteByTaskKey(request.taskKey!!)
                    logger.info("Deleted event record by taskKey: ${request.taskKey}")
                    true
                }

                else -> {
                    logger.warn("Delete request must provide either eventId or taskKey")
                    false
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to delete event record: eventId=${request.eventId}, taskKey=${request.taskKey}", e)
            false
        }
    }

    private fun retryByTaskKey(records: List<TEventRecord>) {
        records.forEach {
            retryEventRecord(it)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventRecordServiceImpl::class.java)
    }
}

