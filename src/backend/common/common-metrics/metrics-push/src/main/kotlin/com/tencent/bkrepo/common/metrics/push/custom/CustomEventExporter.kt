package com.tencent.bkrepo.common.metrics.push.custom

import com.tencent.bkrepo.common.metrics.push.custom.base.CustomEventItem
import com.tencent.bkrepo.common.metrics.push.custom.base.CustomEventPush
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomEventConfig
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CustomEventExporter(
    private val config: CustomEventConfig,
    private val push: CustomEventPush,
    private val scheduler: ThreadPoolTaskScheduler,
) {
    private val queue: ConcurrentLinkedQueue<CustomEventItem> = ConcurrentLinkedQueue()
    private val queueSize = AtomicInteger(0)

    /** 防止定时任务与 shutdown 并发执行 export */
    private val exporting = AtomicBoolean(false)
    private val scheduledTask: ScheduledFuture<*>

    /** 队列满状态标志，避免每次丢弃都打 warn */
    private val queueFullLogged = AtomicBoolean(false)

    init {
        scheduledTask = scheduler.scheduleAtFixedRate(this::runExport, config.pushRate)
    }

    fun reportEvent(item: CustomEventItem) {
        val sizeAfter = queueSize.incrementAndGet()
        if (sizeAfter > config.maxQueueSize) {
            queueSize.decrementAndGet()
            if (queueFullLogged.compareAndSet(false, true)) {
                logger.warn("custom event queue full (>=${config.maxQueueSize}), subsequent drops will be silent")
            } else {
                logger.debug("custom event queue full, drop event: ${item.eventName}")
            }
            return
        }
        val target = item.target.ifEmpty { DEFAULT_TARGET }
        queue.offer(item.copy(target = target))
    }

    /** 定时任务入口：catch Throwable 保证定时器不会因 Error 停摆 */
    private fun runExport() {
        try {
            export()
        } catch (t: Throwable) {
            logger.error("unexpected throwable during event export", t)
        }
    }

    private fun export() {
        if (queue.isEmpty()) return
        if (!exporting.compareAndSet(false, true)) return
        try {
            drainAndPush()
        } finally {
            exporting.set(false)
        }
    }

    /** 失败即丢弃，不重入队；消耗完队列为止 */
    private fun drainAndPush() {
        val batchSize = config.batchSize.coerceAtLeast(1)
        while (queue.isNotEmpty()) {
            val batch = pollBatch(batchSize)
            if (batch.isEmpty()) return
            val success = push.push(batch)
            queueSize.addAndGet(-batch.size)
            if (success) {
                queueFullLogged.set(false)
            } else {
                logger.warn("push failed, dropped ${batch.size} events")
            }
        }
    }

    private fun pollBatch(batchSize: Int): List<CustomEventItem> {
        val batch = ArrayList<CustomEventItem>(batchSize)
        repeat(batchSize) {
            queue.poll()?.let { batch.add(it) }
        }
        return batch
    }

    @PreDestroy
    fun shutdown() {
        scheduledTask.cancel(false)
        waitForInFlightExport()
        logger.info("flushing ${queueSize.get()} remaining custom events before shutdown")
        if (exporting.compareAndSet(false, true)) {
            try {
                drainAndPush()
            } catch (t: Throwable) {
                logger.error("error during shutdown flush", t)
            } finally {
                exporting.set(false)
            }
        }
    }

    /** 等待正在执行的 export 完成，最长等待 SHUTDOWN_WAIT_MS */
    private fun waitForInFlightExport() {
        val deadline = System.currentTimeMillis() + SHUTDOWN_WAIT_MS
        while (exporting.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(SHUTDOWN_POLL_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomEventExporter::class.java)
        private const val SHUTDOWN_WAIT_MS = 5_000L
        private const val SHUTDOWN_POLL_INTERVAL_MS = 50L
        private const val DEFAULT_TARGET = "127.0.0.1"
    }
}
