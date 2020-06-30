package com.tencent.bkrepo.common.quartz.util

import com.tencent.bkrepo.common.quartz.LOCK_INSTANCE_ID
import com.tencent.bkrepo.common.quartz.LOCK_TIME
import com.tencent.bkrepo.common.quartz.dao.SchedulerDao
import org.bson.Document
import org.slf4j.LoggerFactory

class ExpiryCalculator(
    private val schedulerDao: SchedulerDao,
    private val clock: Clock,
    private val jobTimeoutMillis: Long,
    private val triggerTimeoutMillis: Long
) {
    fun isJobLockExpired(lock: Document): Boolean {
        return isLockExpired(lock, jobTimeoutMillis)
    }

    fun isTriggerLockExpired(lock: Document): Boolean {
        val schedulerId = lock.getString(LOCK_INSTANCE_ID)
        return isLockExpired(lock, triggerTimeoutMillis) && hasDefunctScheduler(schedulerId)
    }

    private fun hasDefunctScheduler(schedulerId: String): Boolean {
        val scheduler = schedulerDao.findInstance(schedulerId)
        if (scheduler == null) {
            log.debug("No such scheduler: {}", schedulerId)
            return false
        }
        return scheduler.isDefunct(clock.millis()) && schedulerDao.isNotSelf(scheduler)
    }

    private fun isLockExpired(lock: Document, timeoutMillis: Long): Boolean {
        val lockTime = lock.getDate(LOCK_TIME)
        val elapsedTime = clock.millis() - lockTime.time
        return elapsedTime > timeoutMillis
    }

    companion object {
        private val log = LoggerFactory.getLogger(ExpiryCalculator::class.java)
    }
}
