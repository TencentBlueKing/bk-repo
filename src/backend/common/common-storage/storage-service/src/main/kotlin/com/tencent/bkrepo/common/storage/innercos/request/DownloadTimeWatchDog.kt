package com.tencent.bkrepo.common.storage.innercos.request

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.apache.commons.logging.LogFactory
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor

class DownloadTimeWatchDog(
    private val name: String,
    private val threadPool: ThreadPoolExecutor,
    private val highWaterMark: Long,
    private val lowWaterMark: Long
) {
    var healthyFlag: Boolean = true
    private val sessions: MutableList<DownloadSession> = LinkedList()
    private val lock = Any()
    var taskFuture: ScheduledFuture<*>? = null

    init {
        startWatchTask {
            try {
                checkHealthy()
            } catch (e: Exception) {
                logger.error("Check failed", e)
            }
        }
    }

    fun isHealthy(): Boolean {
        return healthyFlag
    }

    fun add(task: DownloadSession) {
        synchronized(lock) {
            sessions.add(task)
        }
    }

    private fun checkHealthy() {
        var maxSessionLatencyTime = 0L
        var removed = 0
        val size = sessions.size
        val it = sessions.iterator()
        synchronized(lock) {
            while (it.hasNext()) {
                val session = it.next()
                if (session.latencyTime > maxSessionLatencyTime) {
                    maxSessionLatencyTime = session.latencyTime
                }
                if (session.closed) {
                    it.remove()
                    removed++
                }
            }
        }
        val queueSize = threadPool.queue.size
        if (logger.isDebugEnabled) {
            logger.debug(
                "Success to check[$name] sessions[$removed/$size]," +
                    "queue size[$queueSize],max latency $maxSessionLatencyTime ms"
            )
        }
        if (healthyFlag && maxSessionLatencyTime > highWaterMark) {
            healthyFlag = false
            logger.warn("key[$name] change to unhealthy")
        }
        if (!healthyFlag && queueSize < threadPool.corePoolSize && maxSessionLatencyTime < lowWaterMark) {
            healthyFlag = true
            logger.info("key[$name] change to healthy")
        }
    }

    private fun startWatchTask(task: Runnable) {
        taskFutureMap[name]?.let {
            it.cancel(false)
            taskFutureMap.remove(name)
        }
        taskFutureMap.computeIfAbsent(name) {
            timer.scheduleWithFixedDelay(
                task,
                CHECK_INTERVAL,
                CHECK_INTERVAL,
                TimeUnit.MILLISECONDS
            ).apply { taskFuture = this }
        }
    }

    companion object {
        private val logger = LogFactory.getLog(DownloadTimeWatchDog::class.java)
        private val timer = Executors.newSingleThreadScheduledExecutor()
        val taskFutureMap = ConcurrentHashMap<String, Future<*>>()
        private const val CHECK_INTERVAL = 3000L
    }
}
