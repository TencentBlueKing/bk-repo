package com.tencent.bkrepo.common.stream.binder.memory.queue

import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class MessageWorkHandler(private val queue: BlockingQueue<QueueItem>) : Runnable {

    private var isRunning = false

    fun stop() {
        isRunning = false
    }

    @Suppress("TooGenericExceptionCaught")
    override fun run() {
        isRunning = true
        while (isRunning) {
            val item = queue.poll(5, TimeUnit.SECONDS)
            if (item != null) {
                try {
                    val consumers = MemoryListenerContainer.findListener(item.destination)
                    consumers.forEach {
                        it.accept(item.message)
                    }
                } catch (e: Throwable) {
                    logger.error("Memory cloud stream handle fault.", e)
                    if (e is VirtualMachineError) {
                        this.stop()
                    }
                }
            } else {
                Thread.sleep(100)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MemoryMessageQueue::class.java)
    }
}
