package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.model.TOperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLogResponse
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.convert
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveOperateLogProperties
import com.tencent.bkrepo.fs.server.repository.drive.DriveOperateLogDao
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchOp
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchOperation
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveOpLogPageRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveNodeBatchResult
import com.tencent.bkrepo.fs.server.utils.CoroutineRateLimiter
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toKotlinDuration

@Service
class DriveOperateLogService(
    private val properties: DriveOperateLogProperties,
    private val operateLogDao: DriveOperateLogDao,
    coroutineScope: CoroutineScope,
) {
    private val channel = Channel<OperateLog>(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { log -> onUndeliveredLog(log) },
    )
    private val queuedCount = AtomicInteger(0)
    private val droppedCount = AtomicLong(0)
    private val lastDropErrorLogTime = AtomicLong(0)
    private val lastFlushErrorLogTime = AtomicLong(0)
    private val writeRateLimiter = CoroutineRateLimiter(properties.writeLimitQps)
    private val workerJob = coroutineScope.launch {
        runWorkerWithRecovery()
    }

    fun isEnabled(type: String): Boolean {
        return properties.enabled && !properties.disabledTypes.contains(type)
    }

    /**
     * 批量节点操作成功后，按子操作分别记录审计日志。
     */
    suspend fun recordBatchResults(
        batchRequest: DriveNodeBatchRequest,
        batchResult: List<DriveNodeBatchResult>,
        userId: String,
        clientAddress: String,
    ) {
        if (!properties.enabled || batchResult.isEmpty()) {
            return
        }
        batchRequest.operations.zip(batchResult).forEach { (operation, result) ->
            if (result.code != BATCH_SUCCESS_CODE) {
                return@forEach
            }
            val type = batchOpEventType(operation.op)
            if (!isEnabled(type)) {
                return@forEach
            }
            val resourceKey = result.ino?.toString() ?: "unknown"
            record(
                type = type,
                userId = userId,
                clientAddress = clientAddress,
                projectId = batchRequest.projectId,
                repoName = batchRequest.repoName,
                resourceKey = resourceKey,
                description = buildBatchDescription(batchRequest.clientId, operation, result),
            )
        }
    }

    suspend fun record(
        type: String,
        userId: String,
        clientAddress: String,
        projectId: String,
        repoName: String,
        resourceKey: String,
        description: Map<String, Any> = emptyMap(),
    ) {
        if (!isEnabled(type)) {
            return
        }
        val operateLog = OperateLog(
            createdDate = LocalDateTime.now(),
            type = type,
            projectId = projectId,
            repoName = repoName,
            resourceKey = resourceKey,
            userId = userId,
            clientAddress = clientAddress,
            description = description,
        )
        try {
            enqueue(operateLog)
        } catch (e: Exception) {
            logRateLimited(lastFlushErrorLogTime, "Failed to record drive operate log[type=$type]", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        runBlocking {
            channel.close()
            val completed = withTimeoutOrNull(properties.shutdownTimeout.toKotlinDuration()) {
                workerJob.join()
                true
            } ?: false
            if (!completed) {
                workerJob.cancel()
                channel.cancel()
                logRateLimited(
                    lastFlushErrorLogTime,
                    "Timeout waiting drive op log worker shutdown, queuedCount[${queuedCount.get()}]",
                )
            }
        }
    }

    private suspend fun enqueue(log: OperateLog) {
        when (properties.overflowStrategy.trim().uppercase()) {
            OVERFLOW_STRATEGY_BLOCK -> {
                while (!acquireQueueSlot()) {
                    delay(properties.queueBlockRetryInterval.toKotlinDuration())
                }
                var sent = false
                try {
                    channel.send(log)
                    sent = true
                } finally {
                    if (!sent) {
                        releaseQueueSlot()
                    }
                }
            }

            else -> {
                if (!acquireQueueSlot()) {
                    onDropped(log)
                    return
                }
                if (channel.trySend(log).isFailure) {
                    releaseQueueSlot()
                    onDropped(log)
                }
            }
        }
    }

    private fun acquireQueueSlot(): Boolean {
        while (true) {
            val current = queuedCount.get()
            if (current >= properties.queueCapacity) {
                return false
            }
            if (queuedCount.compareAndSet(current, current + 1)) {
                return true
            }
        }
    }

    private fun releaseQueueSlot() {
        queuedCount.updateAndGet { current -> if (current > 0) current - 1 else 0 }
    }

    private suspend fun receiveFromQueue(): OperateLog? {
        return channel.receiveCatching().getOrNull()?.also { releaseQueueSlot() }
    }

    private fun onUndeliveredLog(log: OperateLog) {
        releaseQueueSlot()
        val dropped = droppedCount.incrementAndGet()
        logRateLimited(
            lastDropErrorLogTime,
            "Drive operate log was not delivered[type=${log.type}, projectId=${log.projectId}, " +
                    "repoName=${log.repoName}, userId=${log.userId}, resourceKey=${log.resourceKey}], " +
                    "totalDropped[$dropped]",
        )
    }

    private fun onDropped(log: OperateLog) {
        val dropped = droppedCount.incrementAndGet()
        logRateLimited(
            lastDropErrorLogTime,
            "Drive operate log queue is full, dropped audit log[type=${log.type}, projectId=${log.projectId}, " +
                    "repoName=${log.repoName}, userId=${log.userId}, resourceKey=${log.resourceKey}], " +
                    "totalDropped[$dropped]",
        )
    }

    private suspend fun runWorkerWithRecovery() {
        while (currentCoroutineContext().isActive) {
            try {
                runWorker()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logRateLimited(
                    lastFlushErrorLogTime,
                    "Drive op log worker crashed, restart after ${WORKER_RESTART_DELAY.inWholeMilliseconds}ms",
                    e,
                )
                delay(WORKER_RESTART_DELAY)
            }
        }
    }

    private suspend fun runWorker() {
        val batchSize = properties.batchSize.coerceAtLeast(1)
        val batch = ArrayList<OperateLog>(batchSize)
        while (true) {
            val first = receiveFromQueue() ?: break
            batch.add(first)
            val flushDeadline = System.nanoTime() + properties.flushInterval.toNanos()
            while (batch.size < batchSize) {
                val remainingNanos = flushDeadline - System.nanoTime()
                if (remainingNanos <= 0) {
                    break
                }
                val next = withTimeoutOrNull(remainingNanos.nanoseconds) {
                    receiveFromQueue()
                } ?: break
                batch.add(next)
            }
            flushBatch(batch)
            batch.clear()
        }
    }

    private suspend fun flushBatch(batch: List<OperateLog>) {
        if (batch.isEmpty()) {
            return
        }
        val logs = if (properties.aggregation.enabled) {
            DriveOperateLogAggregator.aggregate(
                batch,
                properties.aggregation.types.toSet(),
            )
        } else {
            batch
        }
        writeRateLimiter.setRate(properties.writeLimitQps)
        val writeLimitQps = properties.writeLimitQps
        val entities = logs.map { it.toEntity() }
        val chunks = if (writeLimitQps > 0) entities.chunked(writeLimitQps) else listOf(entities)
        for (chunk in chunks) {
            try {
                if (writeLimitQps > 0) {
                    writeRateLimiter.acquire(chunk.size)
                }
                operateLogDao.insert(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val dropped = droppedCount.addAndGet(chunk.size.toLong())
                logRateLimited(
                    lastFlushErrorLogTime,
                    "Failed to flush drive op logs[count=${chunk.size}], dropped logs, totalDropped[$dropped]",
                    e,
                )
            }
        }
    }

    /**
     * 分页查询 Drive 操作审计日志
     */
    suspend fun page(request: DriveOpLogPageRequest): Page<OperateLogResponse> {
        val criteria = Criteria.where(TOperateLog::projectId.name).`is`(request.projectId)
            .and(TOperateLog::repoName.name).`is`(request.repoName)
        request.type?.let { criteria.and(TOperateLog::type.name).`is`(it) }
        request.operator?.let { criteria.and(TOperateLog::userId.name).`is`(it) }

        val localStart = parseTime(request.startTime, LocalDateTime.now().minusDays(1L))
        val localEnd = parseTime(request.endTime, LocalDateTime.now())
        criteria.and(TOperateLog::createdDate.name).gte(localStart).lte(localEnd)

        val query = Query(criteria).with(Sort.by(TOperateLog::createdDate.name).descending())
        val pageReq = Pages.ofRequest(request.pageNumber, request.pageSize)
        val totalCount = operateLogDao.count(query)
        val records = operateLogDao.find(query.with(pageReq)).map { convert(it) }
        return Pages.ofResponse(pageReq, totalCount, records)
    }

    private fun OperateLog.toEntity() = TOperateLog(
        createdDate = createdDate,
        type = type,
        projectId = projectId,
        repoName = repoName,
        resourceKey = resourceKey,
        userId = userId,
        clientAddress = clientAddress,
        description = description,
    )

    private fun logRateLimited(lastLogTime: AtomicLong, message: String, cause: Throwable? = null) {
        val intervalMillis = properties.errorLogInterval.toMillis()
        if (intervalMillis > 0) {
            val now = System.currentTimeMillis()
            val previous = lastLogTime.get()
            if (now - previous < intervalMillis) {
                return
            }
            lastLogTime.set(now)
        }
        if (cause == null) {
            logger.error(message)
        } else {
            logger.error(message, cause)
        }
    }

    companion object {
        private const val TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private val WORKER_RESTART_DELAY = 1_000.milliseconds
        private const val OVERFLOW_STRATEGY_BLOCK = "BLOCK"
        private const val BATCH_SUCCESS_CODE = 0
        private val logger = LoggerFactory.getLogger(DriveOperateLogService::class.java)

        private fun batchOpEventType(op: DriveNodeBatchOp): String {
            return when (op) {
                DriveNodeBatchOp.CREATE -> EventType.DRIVE_NODE_CREATE.name
                DriveNodeBatchOp.UPDATE -> EventType.DRIVE_NODE_UPDATE.name
                DriveNodeBatchOp.DELETE -> EventType.DRIVE_NODE_DELETE.name
                DriveNodeBatchOp.RENAME -> EventType.DRIVE_NODE_RENAME.name
            }
        }

        private fun parseTime(value: String?, default: LocalDateTime): LocalDateTime {
            if (value.isNullOrBlank()) return default
            val sdf = SimpleDateFormat(TIME_FORMAT)
            sdf.timeZone = TimeZone.getTimeZone("GMT")
            try {
                return sdf.parse(value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            } catch (_: ParseException) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, value)
            }
        }

        private fun buildBatchDescription(
            clientId: String,
            operation: DriveNodeBatchOperation,
            result: DriveNodeBatchResult,
        ): Map<String, Any> {
            val description = linkedMapOf<String, Any>(
                "clientId" to clientId,
                "op" to operation.op.name,
            )
            result.ino?.let { description["ino"] = it }
            result.nodeId?.let { description["nodeId"] = it }
            result.node?.let { node ->
                if (operation.op != DriveNodeBatchOp.RENAME) {
                    description["size"] = node.size
                }
                node.parent?.let { description["parent"] = it }
                description["name"] = node.name
            }
            return description
        }
    }
}
