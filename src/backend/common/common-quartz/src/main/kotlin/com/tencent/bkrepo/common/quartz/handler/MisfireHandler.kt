package com.tencent.bkrepo.common.quartz.handler

import com.tencent.bkrepo.common.quartz.dao.CalendarDao
import org.quartz.Calendar
import org.quartz.JobPersistenceException
import org.quartz.Trigger
import org.quartz.spi.OperableTrigger
import org.quartz.spi.SchedulerSignaler
import java.util.Date

class MisfireHandler(
    private val calendarDao: CalendarDao,
    private val signaler: SchedulerSignaler,
    private val misfireThreshold: Long
) {

    /**
     * Return true when misfire have been applied and trigger has next fire time.
     *
     * @param trigger    on which apply misfire logic
     * @return true when result of misfire is next fire time
     */
    @Throws(JobPersistenceException::class)
    fun applyMisfireOnRecovery(trigger: OperableTrigger): Boolean {
        if (trigger.misfireInstruction == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) {
            return false
        }
        var cal: Calendar? = null
        if (trigger.calendarName != null) {
            cal = retrieveCalendar(trigger)
        }
        signaler.notifyTriggerListenersMisfired(trigger)
        trigger.updateAfterMisfire(cal)
        return trigger.nextFireTime != null
    }

    @Throws(JobPersistenceException::class)
    fun applyMisfire(trigger: OperableTrigger): Boolean {
        val fireTime = trigger.nextFireTime
        if (misfireIsNotApplicable(trigger, fireTime)) {
            return false
        }
        val cal = retrieveCalendar(trigger)
        signaler.notifyTriggerListenersMisfired(trigger.clone() as OperableTrigger)
        trigger.updateAfterMisfire(cal)
        if (trigger.nextFireTime == null) {
            signaler.notifySchedulerListenersFinalized(trigger)
        } else if (fireTime == trigger.nextFireTime) {
            return false
        }
        return true
    }

    private fun calculateMisfireTime(): Long {
        var misfireTime = System.currentTimeMillis()
        if (misfireThreshold > 0) {
            misfireTime -= misfireThreshold
        }
        return misfireTime
    }

    private fun misfireIsNotApplicable(trigger: OperableTrigger, fireTime: Date?): Boolean {
        return (fireTime == null || isNotMisfired(fireTime)
            || trigger.misfireInstruction == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY)
    }

    private fun isNotMisfired(fireTime: Date): Boolean {
        return calculateMisfireTime() < fireTime.time
    }

    @Throws(JobPersistenceException::class)
    private fun retrieveCalendar(trigger: OperableTrigger): Calendar {
        return calendarDao.retrieveCalendar(trigger.calendarName)!!
    }
}
