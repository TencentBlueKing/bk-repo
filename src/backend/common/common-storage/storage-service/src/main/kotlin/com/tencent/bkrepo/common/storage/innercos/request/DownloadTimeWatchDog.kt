package com.tencent.bkrepo.common.storage.innercos.request

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.apache.commons.logging.LogFactory
import java.util.LinkedList
import java.util.concurrent.ThreadPoolExecutor

class DownloadTimeWatchDog(
    private val name: String,
    private val threadPool: ThreadPoolExecutor,
    private val highWaterMark: Long,
    private val lowWaterMark: Long
) {
    var healthyFlag: Boolean = true
    private val sessions: MutableList<DownloadSession> = LinkedList()
    private var coolingCycleTime = 0L
    private val lock = Any()

    init {
        val runnable = Runnable {
            try {
                checkHealthy()
            } catch (e: Exception) {
                logger.error("Check failed", e)
            }
        }
        timer.scheduleWithFixedDelay(runnable, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
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
        logger.info("Success to check[$name] sessions[$removed/$size],max session latency $maxSessionLatencyTime ms")
        if (healthyFlag && maxSessionLatencyTime > highWaterMark) {
            healthyFlag = false
            coolingCycleTime = System.currentTimeMillis() + COLLING_CYCLE
            logger.warn("key[$name] change to unhealthy")
        }

        if (!healthyFlag && threadPool.queue.size < threadPool.corePoolSize && maxSessionLatencyTime < lowWaterMark) {
            healthyFlag = true
            logger.info("key[$name] change to healthy")
        }
    }

    companion object {
        private val logger = LogFactory.getLog(DownloadTimeWatchDog::class.java)
        private val timer = Executors.newSingleThreadScheduledExecutor()
        private const val CHECK_INTERVAL = 3000L
        private const val COLLING_CYCLE = 60_000L
    }
}
