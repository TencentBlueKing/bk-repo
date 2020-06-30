package com.tencent.bkrepo.common.quartz.cluster

import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.dao.LocksDao
import com.tencent.bkrepo.common.quartz.dao.TriggerDao
import com.tencent.bkrepo.common.quartz.handler.MisfireHandler
import com.tencent.bkrepo.common.quartz.manager.LockManager
import com.tencent.bkrepo.common.quartz.manager.TriggerAndJobManager
import org.quartz.JobPersistenceException
import org.quartz.spi.OperableTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TriggerRecoverer(
    private val locksDao: LocksDao,
    private val manager: TriggerAndJobManager,
    private val lockManager: LockManager,
    private val triggerDao: TriggerDao,
    private val jobDao: JobDao,
    private val recoveryTriggerFactory: RecoveryTriggerFactory,
    private val misfireHandler: MisfireHandler
) {

    @Throws(JobPersistenceException::class)
    fun recover() {
        for (key in locksDao.findOwnTriggersLocks()) {
            val trigger = triggerDao.getTrigger(key) ?: continue

            // Make the trigger's lock fresh for other nodes,
            // so they don't recover it.
            if (locksDao.updateOwnLock(trigger.key)) {
                doRecovery(trigger)
                lockManager.unlockAcquiredTrigger(trigger)
            }
        }
    }

    /**
     * Do recovery procedure after failed run of given trigger.
     *
     * @param trigger    trigger to recover
     * @return recovery trigger or null if its job doesn't want that
     * @throws JobPersistenceException
     */
    @Throws(JobPersistenceException::class)
    fun doRecovery(trigger: OperableTrigger): OperableTrigger? {
        var recoveryTrigger: OperableTrigger? = null
        if (jobDao.requestsRecovery(trigger.jobKey)) {
            recoveryTrigger = recoverTrigger(trigger)
            if (!wasOneShotTrigger(trigger)) {
                updateMisfires(trigger)
            }
        } else if (wasOneShotTrigger(trigger)) {
            cleanUpFailedRun(trigger)
        } else {
            updateMisfires(trigger)
        }
        return recoveryTrigger
    }

    @Throws(JobPersistenceException::class)
    private fun recoverTrigger(trigger: OperableTrigger): OperableTrigger {
        log.info("Recovering trigger: ${trigger.key}")
        val recoveryTrigger = recoveryTriggerFactory.from(trigger)
        manager.storeTrigger(recoveryTrigger, false)
        return recoveryTrigger
    }

    @Throws(JobPersistenceException::class)
    private fun updateMisfires(trigger: OperableTrigger) {
        if (misfireHandler.applyMisfireOnRecovery(trigger)) {
            log.info("Misfire applied. Replacing trigger: ${trigger.key}")
            manager.storeTrigger(trigger, true)
        } else {
            //TODO should complete trigger?
            log.warn("Recovery misfire not applied for trigger: ${trigger.key}")
            //            storeTrigger(conn, trig,
//                    null, true, STATE_COMPLETE, forceState, recovering);
//            schedSignaler.notifySchedulerListenersFinalized(trig);
        }
    }

    private fun cleanUpFailedRun(trigger: OperableTrigger) {
        manager.removeTrigger(trigger.key)
    }

    private fun wasOneShotTrigger(trigger: OperableTrigger): Boolean {
        return trigger.nextFireTime == null && trigger.startTime == trigger.finalFireTime
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TriggerRecoverer::class.java)
    }

}
