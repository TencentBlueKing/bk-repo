package com.tencent.bkrepo.repository.job.clean


import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.Trigger
import org.quartz.UnableToInterruptJobException
import org.quartz.impl.matchers.GroupMatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CleanRepoTaskScheduler(
    private val scheduler: Scheduler
) {
    fun scheduleJob(jobDetail: JobDetail, trigger: Trigger) {
        try {
            scheduler.scheduleJob(jobDetail, trigger)
            logger.info("Success to schedule job[${jobDetail.key}]")
        } catch (exception: SchedulerException) {
            logger.error("Failed to schedule job[${jobDetail.key}]", exception)
        }
    }

    fun interruptJob(id: String) {
        val jobKey = JobKey.jobKey(id, CLEAN_JOB_GROUP)
        try {
            scheduler.interrupt(jobKey)
            logger.info("Success to interrupt job[$jobKey]")
        } catch (exception: UnableToInterruptJobException) {
            logger.error("Failed to interrupt job[$id]", exception)
        }
    }

    fun deleteJob(id: String) {
        val jobKey = JobKey.jobKey(id, CLEAN_JOB_GROUP)
        try {
            interruptJob(id)
            scheduler.deleteJob(jobKey)
            logger.info("Success to delete job[$jobKey]")
        } catch (exception: SchedulerException) {
            logger.error("Failed to delete job[$id]", exception)
        }
    }

    fun exist(id: String): Boolean {
        return try {
            scheduler.checkExists(JobKey.jobKey(id, CLEAN_JOB_GROUP))
        } catch (exception: SchedulerException) {
            logger.error("Failed to check exist job[$id].", exception)
            false
        }
    }

    fun listJobKeys(): Set<JobKey> {
        return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(CLEAN_JOB_GROUP))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CleanRepoTaskScheduler::class.java)

        /**
         * quartz scheduler的job group名称
         */
        const val CLEAN_JOB_GROUP = "CLEAN"
    }
}
