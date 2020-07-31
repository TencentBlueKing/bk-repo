package com.tencent.bkrepo.common.stream.binder.memory.queue

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MemoryMessageQueue {

    private var startingSync = Any()
    private var isRunning: Boolean = false
    private var queue: LinkedBlockingQueue<QueueItem>? = null
    private var handlers: List<MessageWorkHandler>? = null

    fun start(queueSize: Int, workerPoolSize: Int? = null) {
        if (!isRunning) {
            synchronized(startingSync) {
                if (!isRunning) {
                    val defaultSize = workerPoolSize ?: -1
                    val concurrentLevel = if (defaultSize <= 0) {
                        (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                    } else {
                        defaultSize
                    }

                    queue = LinkedBlockingQueue(queueSize)
                    this.startHandlers(concurrentLevel)
                    Runtime.getRuntime().addShutdownHook(
                        Thread {
                            this.shutdown()
                        }
                    )
                    isRunning = true
                    logger.info("Cloud stream memory queue was started ( qs: $queueSize, ps: $concurrentLevel ).")
                }
            }
        }
    }

    private fun startHandlers(poolSize: Int) {
        this.handlers = this.queue?.run {
            (0 until poolSize).map {
                val handler = MessageWorkHandler(this)
                threadFactory.newThread(handler).start()
                handler
            }
        }
    }

    fun shutdown() {
        if (isRunning) {
            synchronized(startingSync) {
                if (isRunning) {
                    this.handlers?.forEach {
                        it.stop()
                    }
                    logger.info("Cloud stream memory queue was stopped.")
                    isRunning = false
                }
            }
        }
    }

    fun produce(destination: String, message: Message<*>) {
        if (!isRunning) {
            throw IllegalStateException("MemoryMessageQueue is not running.")
        }
        var added = false
        while (!added) {
            added = queue?.offer(QueueItem(message, destination), 2, TimeUnit.SECONDS) ?: true
            if (!added) {
                queue?.poll()
                logger.warn("Message queue was full, the earlier messages have been dropped.")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MemoryMessageQueue::class.java)
        private val index: AtomicLong = AtomicLong()
        private val threadFactory = ThreadFactory {
            Thread(it).apply {
                this.isDaemon = true
                this.name = "stream-memory-queue-poll-${index.incrementAndGet()}"
            }
        }

        val instance: MemoryMessageQueue by lazy {
            MemoryMessageQueue()
        }
    }
}
