package com.tencent.bkrepo.archive.job

import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

/**
 * 任务进度监控器
 * 定时打印任务进度
 * */
class JobProcessMonitor {
    private val contexts = mutableMapOf<String, JobContext>()

    private var running = false

    /**
     * 启动任务进度监控
     * */
    fun start() {
        if (running) {
            return
        }
        running = true
        thread(name = NAME, isDaemon = true) {
            while (running) {
                Thread.sleep(TIME_INTERVAL)
                contexts.forEach { (id, context) ->
                    logger.info("Process info($id): $context")
                }
            }
        }
    }

    /**
     * 停止任务进度监控
     * */
    fun stop() {
        running = false
    }

    /**
     * 添加监控
     * */
    fun addMonitor(id: String, context: JobContext) {
        contexts[id] = context
    }

    /**
     * 移除监控
     * */
    fun removeMonitor(id: String) {
        contexts.remove(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JobProcessMonitor::class.java)
        private const val TIME_INTERVAL = 2000L
        private const val NAME = "job-process-monitor"
    }
}
