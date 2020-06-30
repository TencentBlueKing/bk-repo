package com.tencent.bkrepo.common.quartz.manager

import com.tencent.bkrepo.common.quartz.TRIGGER_JOB_ID
import com.tencent.bkrepo.common.quartz.converter.JobConverter
import com.tencent.bkrepo.common.quartz.converter.TriggerConverter
import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.dao.TriggerDao
import com.tencent.bkrepo.common.quartz.util.Keys
import org.bson.Document
import org.bson.types.ObjectId
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.TriggerKey
import org.quartz.spi.OperableTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TriggerAndJobManager(
    private val triggerDao: TriggerDao,
    private val jobDao: JobDao,
    private val triggerConverter: TriggerConverter
) {

    @Throws(JobPersistenceException::class)
    fun getTriggersForJob(jobKey: JobKey): List<OperableTrigger> {
        return jobDao.getJob(jobKey)?.let { triggerDao.getTriggersForJob(it) } ?: emptyList()
    }

    fun removeJob(jobKey: JobKey): Boolean {
        val keyObject = Keys.toFilter(jobKey)
        val item = jobDao.getJob(keyObject)
        if (item != null) {
            jobDao.remove(keyObject)
            triggerDao.removeByJobId(item["_id"]!!)
            return true
        }
        return false
    }

    @Throws(JobPersistenceException::class)
    fun removeJobs(jobKeys: List<JobKey>): Boolean {
        for (key in jobKeys) {
            removeJob(key)
        }
        // TODO: true or false
        return false
    }

    fun removeTrigger(triggerKey: TriggerKey): Boolean {
        val filter = Keys.toFilter(triggerKey)
        val trigger = triggerDao.findTrigger(filter)
        if (trigger != null) {
            removeOrphanedJob(trigger)
            //TODO: check if can .deleteOne(filter) here
            triggerDao.remove(filter)
            return true
        }
        return false
    }

    @Throws(JobPersistenceException::class)
    fun removeTriggers(triggerKeys: List<TriggerKey>): Boolean {
        //FIXME return boolean allFound = true when all removed
        for (key in triggerKeys) {
            removeTrigger(key)
        }
        return false
    }

    fun removeTriggerWithoutNextFireTime(trigger: OperableTrigger): Boolean {
        if (trigger.nextFireTime == null) {
            log.debug("Removing trigger ${trigger.key} as it has no next fire time.")
            removeTrigger(trigger.key)
            return true
        }
        return false
    }

    @Throws(JobPersistenceException::class)
    fun replaceTrigger(triggerKey: TriggerKey, newTrigger: OperableTrigger): Boolean {
        val oldTrigger = triggerDao.getTrigger(triggerKey) ?: return false
        if (oldTrigger.jobKey != newTrigger.jobKey) {
            throw JobPersistenceException("New trigger is not related to the same job as the old trigger.")
        }
        removeOldTrigger(triggerKey)
        copyOldJobDataMap(newTrigger, oldTrigger)
        storeNewTrigger(newTrigger, oldTrigger)
        return true
    }

    @Throws(JobPersistenceException::class)
    fun storeJobAndTrigger(newJob: JobDetail, newTrigger: OperableTrigger) {
        val jobId = jobDao.storeJobInMongo(newJob, false)
        log.debug(
            "Storing job {} and trigger {}",
            newJob.key,
            newTrigger.key
        )
        storeTrigger(newTrigger, jobId, false)
    }

    @Throws(JobPersistenceException::class)
    fun storeTrigger(newTrigger: OperableTrigger, replaceExisting: Boolean) {
        if (newTrigger.jobKey == null) {
            throw JobPersistenceException("Trigger must be associated with a job. Please specify a JobKey.")
        }
        val doc = jobDao.getJob(Keys.toFilter(newTrigger.jobKey))
        if (doc != null) {
            storeTrigger(newTrigger, doc.getObjectId("_id"), replaceExisting)
        } else {
            throw JobPersistenceException("Could not find job with key " + newTrigger.jobKey)
        }
    }

    private fun copyOldJobDataMap(newTrigger: OperableTrigger, trigger: OperableTrigger) {
        // Copy across the job data map from the old trigger to the new one.
        newTrigger.jobDataMap.putAll(trigger.jobDataMap)
    }

    private fun isNotDurable(job: Document): Boolean {
        return !job.containsKey(JobConverter.JOB_DURABILITY) ||
            job[JobConverter.JOB_DURABILITY].toString() == "false"
    }

    private fun isOrphan(job: Document?): Boolean {
        return job != null && isNotDurable(job) && triggerDao.hasLastTrigger(job)
    }

    private fun removeOldTrigger(triggerKey: TriggerKey) {
        // Can't call remove trigger as if the job is not durable, it will remove the job too
        triggerDao.remove(triggerKey)
    }

    // If the removal of the Trigger results in an 'orphaned' Job that is not 'durable',
    // then the job should be removed also.
    private fun removeOrphanedJob(trigger: Document) {
        if (trigger.containsKey(TRIGGER_JOB_ID)) {
            // There is only 1 job per trigger so no need to look further.
            val job = jobDao.getById(trigger[TRIGGER_JOB_ID]!!)
            if (isOrphan(job)) {
                jobDao.remove(job!!)
            }
        } else {
            log.debug("The trigger had no associated jobs")
        }
    }

    @Throws(JobPersistenceException::class)
    private fun storeNewTrigger(newTrigger: OperableTrigger, oldTrigger: OperableTrigger) {
        try {
            storeTrigger(newTrigger, false)
        } catch (jpe: JobPersistenceException) {
            storeTrigger(oldTrigger, false)
            throw jpe
        }
    }

    @Throws(JobPersistenceException::class)
    private fun storeTrigger(newTrigger: OperableTrigger, jobId: ObjectId, replaceExisting: Boolean) {
        val trigger = triggerConverter.toDocument(newTrigger, jobId)
        if (replaceExisting) {
            trigger.remove("_id")
            triggerDao.replace(newTrigger.key, trigger)
        } else {
            triggerDao.insert(trigger, newTrigger)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TriggerAndJobManager::class.java)
    }

}
