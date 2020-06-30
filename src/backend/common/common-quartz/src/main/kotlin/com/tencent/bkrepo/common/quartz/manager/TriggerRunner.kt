package com.tencent.bkrepo.common.quartz.manager

import com.mongodb.MongoWriteException
import com.tencent.bkrepo.common.quartz.STATE_ERROR
import com.tencent.bkrepo.common.quartz.STATE_WAITING
import com.tencent.bkrepo.common.quartz.cluster.TriggerRecoverer
import com.tencent.bkrepo.common.quartz.converter.TriggerConverter
import com.tencent.bkrepo.common.quartz.dao.CalendarDao
import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.dao.LocksDao
import com.tencent.bkrepo.common.quartz.dao.TriggerDao
import com.tencent.bkrepo.common.quartz.handler.MisfireHandler
import org.quartz.Calendar
import org.quartz.JobDetail
import org.quartz.JobPersistenceException
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.quartz.spi.OperableTrigger
import org.quartz.spi.TriggerFiredBundle
import org.quartz.spi.TriggerFiredResult
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections.sort
import java.util.Comparator
import java.util.Date

class TriggerRunner(
    private val triggerAndJobManager: TriggerAndJobManager,
    private val triggerDao: TriggerDao,
    private val jobDao: JobDao,
    private val locksDao: LocksDao,
    private val calendarDao: CalendarDao,
    private val misfireHandler: MisfireHandler,
    private val triggerConverter: TriggerConverter,
    private val lockManager: LockManager,
    private val recoverer: TriggerRecoverer
) {
    @Throws(JobPersistenceException::class)
    fun acquireNext(noLaterThan: Long, maxCount: Int, timeWindow: Long): List<OperableTrigger> {
        val noLaterThanDate = Date(noLaterThan + timeWindow)
        log.debug("Finding up to $maxCount triggers which have time less than $noLaterThanDate")
        val triggers = acquireNextTriggers(noLaterThanDate, maxCount)

        // Because we are handling a batch, we may have done multiple queries and while the result for each
        // query is in fire order, the result for the whole might not be, so sort them again
        sort(triggers, NEXT_FIRE_TIME_COMPARATOR)
        return triggers
    }

    @Throws(JobPersistenceException::class)
    fun triggersFired(triggers: List<OperableTrigger>): List<TriggerFiredResult> {
        val results: MutableList<TriggerFiredResult> =
            ArrayList(triggers.size)
        for (trigger in triggers) {
            log.debug("Fired trigger {}", trigger.key)
            val bundle = createTriggerFiredBundle(trigger)
            if (hasJobDetail(bundle)) {
                val job = bundle!!.jobDetail
                try {
                    lockManager.lockJob(job)
                    results.add(TriggerFiredResult(bundle))
                    triggerAndJobManager.storeTrigger(trigger, true)
                } catch (dk: MongoWriteException) {
                    log.debug("Job disallows concurrent execution and is already running ${job.key}")
                    locksDao.unlockTrigger(trigger)
                    lockManager.unlockExpired(job)
                }
            }
        }
        return results
    }

    @Throws(JobPersistenceException::class)
    private fun acquireNextTriggers(noLaterThanDate: Date, maxCount: Int): List<OperableTrigger> {
        val triggers: MutableMap<TriggerKey, OperableTrigger> = HashMap()
        for (triggerDoc in triggerDao.findEligibleToRun(noLaterThanDate)) {
            if (acquiredEnough(triggers, maxCount)) {
                break
            }
            val trigger = triggerConverter.toTriggerWithOptionalJob(triggerDoc)
            if (cannotAcquire(triggers, trigger)) {
                continue
            }
            if (trigger.jobKey == null) {
                log.error("Error retrieving job for trigger ${trigger.key}, setting trigger state to ERROR.")
                triggerDao.transferState(trigger.key, STATE_WAITING, STATE_ERROR)
                continue
            }
            val key = trigger.key
            if (lockManager.tryLock(key)) {
                if (prepareForFire(noLaterThanDate, trigger)) {
                    log.info("Acquired trigger: {}", trigger.key)
                    triggers[trigger.key] = trigger
                } else {
                    lockManager.unlockAcquiredTrigger(trigger)
                }
            } else if (lockManager.relockExpired(key)) {
                log.info("Recovering trigger: {}", trigger.key)
                val recoveryTrigger = recoverer.doRecovery(trigger)
                lockManager.unlockAcquiredTrigger(trigger)
                if (recoveryTrigger != null && lockManager.tryLock(recoveryTrigger.key)) {
                    log.info("Acquired trigger: {}", recoveryTrigger.key)
                    triggers[recoveryTrigger.key] = recoveryTrigger
                }
            }
        }
        return ArrayList(triggers.values)
    }

    @Throws(JobPersistenceException::class)
    private fun prepareForFire(noLaterThanDate: Date, trigger: OperableTrigger): Boolean {
        //TODO don't remove when recovering trigger
        if (triggerAndJobManager.removeTriggerWithoutNextFireTime(trigger)) {
            return false
        }
        return !notAcquirableAfterMisfire(noLaterThanDate, trigger)
    }

    private fun acquiredEnough(triggers: Map<TriggerKey, OperableTrigger>, maxCount: Int): Boolean {
        return maxCount <= triggers.size
    }

    private fun cannotAcquire(triggers: Map<TriggerKey, OperableTrigger>, trigger: OperableTrigger?): Boolean {
        if (trigger == null) {
            return true
        }
        if (triggers.containsKey(trigger.key)) {
            log.debug("Skipping trigger {} as we have already acquired it.", trigger.key)
            return true
        }
        return false
    }

    @Throws(JobPersistenceException::class)
    private fun createTriggerFiredBundle(trigger: OperableTrigger): TriggerFiredBundle? {
        val cal = calendarDao.retrieveCalendar(trigger.calendarName)
        if (expectedCalendarButNotFound(trigger, cal)) {
            return null
        }
        val prevFireTime = trigger.previousFireTime
        trigger.triggered(cal)
        return TriggerFiredBundle(
            retrieveJob(trigger), trigger, cal,
            isRecovering(trigger), Date(),
            trigger.previousFireTime, prevFireTime,
            trigger.nextFireTime
        )
    }

    private fun expectedCalendarButNotFound(trigger: OperableTrigger, cal: Calendar?): Boolean {
        return trigger.calendarName != null && cal == null
    }

    private fun isRecovering(trigger: OperableTrigger): Boolean {
        return trigger.key.group == Scheduler.DEFAULT_RECOVERY_GROUP
    }

    private fun hasJobDetail(bundle: TriggerFiredBundle?): Boolean {
        return bundle != null && bundle.jobDetail != null
    }

    @Throws(JobPersistenceException::class)
    private fun notAcquirableAfterMisfire(noLaterThanDate: Date, trigger: OperableTrigger): Boolean {
        if (misfireHandler.applyMisfire(trigger)) {
            triggerAndJobManager.storeTrigger(trigger, true)
            log.debug("Misfire trigger {}.", trigger.key)
            if (triggerAndJobManager.removeTriggerWithoutNextFireTime(trigger)) {
                return true
            }

            // The trigger has misfired and was rescheduled, its firetime may be too far in the future
            // and we don't want to hang the quartz scheduler thread up on <code>sigLock.wait(timeUntilTrigger);</code>
            // so, check again that the trigger is due to fire
            if (trigger.nextFireTime.after(noLaterThanDate)) {
                log.debug("Skipping trigger ${trigger.key} as it misfired and was scheduled for ${trigger.nextFireTime}.")
                return true
            }
        }
        return false
    }

    @Throws(JobPersistenceException::class)
    private fun retrieveJob(trigger: OperableTrigger): JobDetail {
        return try {
            jobDao.retrieveJob(trigger.jobKey)!!
        } catch (e: JobPersistenceException) {
            locksDao.unlockTrigger(trigger)
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TriggerRunner::class.java)
        private val NEXT_FIRE_TIME_COMPARATOR = Comparator<OperableTrigger> { o1, o2 -> (o1.nextFireTime.time - o2.nextFireTime.time).toInt() }
    }
}
