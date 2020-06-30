package com.tencent.bkrepo.common.quartz.manager

import com.mongodb.MongoWriteException
import com.tencent.bkrepo.common.quartz.LOCK_TIME
import com.tencent.bkrepo.common.quartz.dao.LocksDao
import com.tencent.bkrepo.common.quartz.util.ExpiryCalculator
import org.quartz.JobDetail
import org.quartz.TriggerKey
import org.quartz.spi.OperableTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LockManager(
    private val locksDao: LocksDao,
    private val expiryCalculator: ExpiryCalculator
) {

    /**
     * Lock job if it doesn't allow concurrent executions.
     *
     * @param job    job to lock
     */
    fun lockJob(job: JobDetail) {
        if (job.isConcurrentExectionDisallowed) {
            locksDao.lockJob(job)
        }
    }

    fun unlockAcquiredTrigger(trigger: OperableTrigger) {
        locksDao.unlockTrigger(trigger)
    }

    /**
     * Unlock job that have existing, expired lock.
     *
     * @param job    job to potentially unlock
     */
    fun unlockExpired(job: JobDetail) {
        val existingLock = locksDao.findJobLock(job.key)
        if (existingLock != null) {
            if (expiryCalculator.isJobLockExpired(existingLock)) {
                log.debug("Removing expired lock for job ${job.key}")
                locksDao.remove(existingLock)
            }
        }
    }

    /**
     * Try to lock given trigger, ignoring errors.
     * @param key    trigger to lock
     * @return true when successfully locked, false otherwise
     */
    fun tryLock(key: TriggerKey): Boolean {
        try {
            locksDao.lockTrigger(key)
            return true
        } catch (e: MongoWriteException) {
            log.info("Failed to lock trigger $key, reason: ${e.error}")
        }
        return false
    }

    /**
     * Relock trigger if its lock has expired.
     *
     * @param key    trigger to lock
     * @return true when successfully relocked
     */
    fun relockExpired(key: TriggerKey): Boolean {
        val existingLock = locksDao.findTriggerLock(key)
        if (existingLock != null) {
            if (expiryCalculator.isTriggerLockExpired(existingLock)) {
                // When a scheduler is defunct then its triggers become expired
                // after sometime and can be recovered by other schedulers.
                // To check that a trigger is owned by a defunct scheduler we evaluate
                // its LOCK_TIME and try to reassign it to this scheduler.
                // Relock may not be successful when some other scheduler has done
                // it first.
                log.info("Trigger $key is expired - re-locking")
                return locksDao.relock(key, existingLock.getDate(LOCK_TIME))
            } else {
                log.info("Trigger {} hasn't expired yet. Lock time: {}", key, existingLock.getDate(LOCK_TIME))
            }
        } else {
            log.warn("Error retrieving expired lock from the database for trigger $key. Maybe it was deleted")
        }
        return false
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(LockManager::class.java)
    }

}
