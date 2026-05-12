package com.tencent.bkrepo.common.metrics.push.custom

import com.tencent.bkrepo.common.metrics.push.custom.base.CustomEventItem
import com.tencent.bkrepo.common.metrics.push.custom.base.CustomEventPush
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomEventConfig
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class CustomEventExporter(
    private val config: CustomEventConfig,
    private val push: CustomEventPush,
    private val scheduler: ThreadPoolTaskScheduler,
) {
    private val queue: LinkedBlockingQueue<CustomEventItem> = LinkedBlockingQueue(config.maxQueueSize)

    /** 保护 export 与 shutdown 不并发执行 */
    private val lock = ReentrantLock()
    private val scheduledTask: ScheduledFuture<*>

    /** 队列满状态标志，避免每次丢弃都打 warn */
    private val queueFullLogged = AtomicBoolean(false)

    init {
        scheduledTask = scheduler.scheduleAtFixedRate(this::runExport, config.pushRate)
    }

    fun reportEvent(item: CustomEventItem) {
        val target = item.target.ifEmpty { DEFAULT_TARGET }
        val offered = queue.offer(item.copy(target = target))
        if (!offered) {
            if (queueFullLogged.compareAndSet(false, true)) {
                logger.warn("custom event queue full (>=${config.maxQueueSize}), subsequent drops will be silent")
            } else {
                logger.debug("custom event queue full, drop event: ${item.eventName}")
            }
        }
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
        if (!lock.tryLock()) return
        try {
            drainAndPush()
        } finally {
            lock.unlock()
        }
    }

    /** 失败即丢弃，不重入队；消耗完队列为止 */
    private fun drainAndPush() {
        val batchSize = config.batchSize.coerceAtLeast(1)
        while (queue.isNotEmpty()) {
            val batch = pollBatch(batchSize)
            if (batch.isEmpty()) return
            val success = push.push(batch)
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
        // 持有锁等待正在进行的 export 完成，然后执行最终 flush
        lock.lock()
        try {
            logger.info("flushing ${queue.size} remaining custom events before shutdown")
            drainAndPush()
        } catch (t: Throwable) {
            logger.error("error during shutdown flush", t)
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomEventExporter::class.java)
        private const val DEFAULT_TARGET = "127.0.0.1"
    }
}
