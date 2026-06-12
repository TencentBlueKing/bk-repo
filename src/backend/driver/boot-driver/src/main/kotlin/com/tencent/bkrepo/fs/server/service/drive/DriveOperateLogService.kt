package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.service.log.ROperateLogService
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveOperateLogProperties
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchOperation
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchOp
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchRequest
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toKotlinDuration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Service
class DriveOperateLogService(
    private val properties: DriveOperateLogProperties,
    private val operateLogService: ROperateLogService,
    coroutineScope: CoroutineScope,
) {
    private val channel = Channel<OperateLog>(Channel.UNLIMITED)
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
        workerJob.cancel()
        flushRemaining()
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

    private suspend fun receiveFromQueue(): OperateLog {
        return channel.receive().also { releaseQueueSlot() }
    }

    private fun pollFromQueue(): OperateLog? {
        return channel.tryReceive().getOrNull()?.also { releaseQueueSlot() }
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
                    "Drive operate log worker crashed, will restart after ${WORKER_RESTART_DELAY.inWholeMilliseconds}ms",
                    e,
                )
                delay(WORKER_RESTART_DELAY)
            }
        }
    }

    private suspend fun runWorker() {
        val batchSize = properties.batchSize.coerceAtLeast(1)
        val batch = ArrayList<OperateLog>(batchSize)
        while (currentCoroutineContext().isActive) {
            batch.add(receiveFromQueue())
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
            if (batch.isNotEmpty()) {
                flushBatch(batch)
                batch.clear()
            }
        }
    }

    private fun flushRemaining() {
        runBlocking {
            val batchSize = properties.batchSize.coerceAtLeast(1)
            while (true) {
                val batch = ArrayList<OperateLog>(batchSize)
                while (batch.size < batchSize) {
                    val log = pollFromQueue() ?: break
                    batch.add(log)
                }
                if (batch.isEmpty()) {
                    break
                }
                flushBatch(batch)
            }
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
        val chunks = if (writeLimitQps > 0) logs.chunked(writeLimitQps) else listOf(logs)
        for (chunk in chunks) {
            try {
                if (writeLimitQps > 0) {
                    writeRateLimiter.acquire(chunk.size)
                }
                operateLogService.save(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val dropped = droppedCount.addAndGet(chunk.size.toLong())
                logRateLimited(
                    lastFlushErrorLogTime,
                    "Failed to flush drive operate logs[count=${chunk.size}], dropped audit logs, totalDropped[$dropped]",
                    e,
                )
            }
        }
    }

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
