package com.tencent.bkrepo.archive.job

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

abstract class BufferedResourceManager<T>(
    val queue: BlockingQueue<T>,
    private val maxConcurrency: Int,
) : ResourceManager<T, TaskResult> {

    // 创建一个信号量，初始值为 maxConcurrency
    private val semaphore = Semaphore(maxConcurrency)

    // 创建一个原子布尔值，用于表示资源管理器是否关闭
    private val closed = AtomicBoolean(false)

    // 处理资源的主要方法，当资源可用时，将其传递给 process0 方法
    override fun process(resource: T): Mono<TaskResult> {
        if (closed.get()) {
            return Mono.error(RuntimeException("Resource manager is closed."))
        }
        val mono = if (semaphore.tryAcquire()) {
            process0(resource)
        } else {
            val result = enqueue(resource)
            if (!result) {
                logger.warn("queue is full")
            }
            Mono.just(TaskResult.QUEUED)
        }
        return mono.doOnSuccess {
            if (it != TaskResult.QUEUED) {
                semaphore.release()
                val poll = queue.poll()
                if (poll != null) {
                    process(poll).subscribe()
                }
            }
        }
    }

    // 判断资源管理器是否忙碌
    override fun isBusy(): Boolean {
        return semaphore.availablePermits() == 0
    }

    // 抽象方法，实现资源处理的主要逻辑
    protected abstract fun process0(resource: T): Mono<TaskResult>

    // 重写方法，将资源添加到队列中
    open fun enqueue(resource: T): Boolean = queue.offer(resource)

    // 启动资源管理器
    override fun start() {
        if (closed.compareAndSet(true, false)) {
            logger.info("Starting buffered resource manager.")
        }
    }

    // 停止资源管理器
    override fun stop() {
        if (closed.compareAndSet(false, true)) {
            logger.info("Stopping buffered resource manager.")
        }
    }

    // 返回队列中剩余的空间数量
    fun remainingCapacity(): Int {
        if (queue.isNotEmpty()) {
            return 0
        }
        return semaphore.availablePermits()
    }

    // 初始化一个记录器，用于记录日志
    companion object {
        private val logger = LoggerFactory.getLogger(BufferedResourceManager::class.java)
    }
}
