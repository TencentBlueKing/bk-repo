package com.tencent.bkrepo.common.quartz.handler

import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.dao.LocksDao
import com.tencent.bkrepo.common.quartz.dao.TriggerDao
import com.tencent.bkrepo.common.quartz.manager.TriggerAndJobManager
import org.quartz.JobDetail
import org.quartz.JobPersistenceException
import org.quartz.Trigger.CompletedExecutionInstruction
import org.quartz.spi.OperableTrigger
import org.quartz.spi.SchedulerSignaler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JobCompleteHandler(
    private val triggerAndJobManager: TriggerAndJobManager,
    private val signaler: SchedulerSignaler,
    private val jobDao: JobDao,
    private val locksDao: LocksDao,
    private val triggerDao: TriggerDao
) {

    fun jobComplete(trigger: OperableTrigger, job: JobDetail, executionInstruction: CompletedExecutionInstruction) {
        log.debug("Trigger completed {}", trigger.key)
        if (job.isPersistJobDataAfterExecution) {
            if (job.jobDataMap.isDirty) {
                log.debug("Job data map dirty, will store {}", job.key)
                try {
                    jobDao.storeJobInMongo(job, true)
                } catch (e: JobPersistenceException) {
                    throw RuntimeException(e)
                }
            }
        }
        if (job.isConcurrentExectionDisallowed) {
            locksDao.unlockJob(job)
        }
        try {
            process(trigger, executionInstruction)
        } catch (e: JobPersistenceException) {
            throw RuntimeException(e)
        }
        locksDao.unlockTrigger(trigger)
    }

    private fun isTriggerDeletionRequested(triggerInstCode: CompletedExecutionInstruction): Boolean {
        return triggerInstCode == CompletedExecutionInstruction.DELETE_TRIGGER
    }

    @Throws(JobPersistenceException::class)
    private fun process(trigger: OperableTrigger, executionInstruction: CompletedExecutionInstruction) {
        // check for trigger deleted during execution...
        val dbTrigger = triggerDao.getTrigger(trigger.key)
        if (dbTrigger != null) {
            if (isTriggerDeletionRequested(executionInstruction)) {
                if (trigger.nextFireTime == null) {
                    // double check for possible reschedule within job
                    // execution, which would cancel the need to delete...
                    if (dbTrigger.nextFireTime == null) {
                        triggerAndJobManager.removeTrigger(trigger.key)
                    }
                } else {
                    triggerAndJobManager.removeTrigger(trigger.key)
                    signaler.signalSchedulingChange(0L)
                }
            } else if (executionInstruction == CompletedExecutionInstruction.SET_TRIGGER_COMPLETE) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L)
            } else if (executionInstruction == CompletedExecutionInstruction.SET_TRIGGER_ERROR) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L)
            } else if (executionInstruction == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L)
            } else if (executionInstruction == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_COMPLETE) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L)
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(JobCompleteHandler::class.java)
    }
}
