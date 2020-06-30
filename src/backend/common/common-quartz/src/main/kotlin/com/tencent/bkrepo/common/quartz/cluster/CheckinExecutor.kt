package com.tencent.bkrepo.common.quartz.cluster

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CheckinExecutor(
    private val checkinTask: CheckinTask,
    private val checkinIntervalMillis: Long,
    private val instanceId: String
) {
    private val executor = Executors.newScheduledThreadPool(1)

    /**
     * Start execution of CheckinTask.
     */
    fun start() {
        log.info("Starting check-in task for scheduler instance: {}", instanceId)
        executor.scheduleAtFixedRate(
            checkinTask,
            INITIAL_DELAY.toLong(),
            checkinIntervalMillis,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Stop execution of CheckinTask.
     */
    fun shutdown() {
        log.info("Stopping CheckinExecutor for scheduler instance: {}", instanceId)
        executor.shutdown()
    }

    companion object {
        private val log = LoggerFactory.getLogger(CheckinExecutor::class.java)
        // Arbitrary value:
        private const val INITIAL_DELAY = 0
    }
}
