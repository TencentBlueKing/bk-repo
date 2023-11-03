package com.tencent.bkrepo.archive.job

import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

/**
 * 流程监控器
 * */
class JobProcessMonitor {
    val contexts = mutableMapOf<String, JobContext>()

    private var running = false
    fun start() {
        if (running) {
            return
        }
        running = true
        thread(name = "job-process-monitor", isDaemon = true) {
            while (running) {
                Thread.sleep(2000)
                contexts.forEach { (id, context) ->
                    logger.info("Process info($id): $context")
                }
            }
        }
    }

    fun stop() {
        running = false
    }

    fun addMonitor(id: String, context: JobContext) {
        contexts[id] = context
    }

    fun removeMonitor(id: String) {
        contexts.remove(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JobProcessMonitor::class.java)
    }
}
