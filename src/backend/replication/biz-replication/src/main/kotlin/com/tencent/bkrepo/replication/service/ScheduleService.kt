package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
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
class ScheduleService(
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

    fun listJobKeys(): Set<JobKey> {
        return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(DEFAULT_GROUP_ID))
    }

    fun interruptJob(id: String) {
        val jobKey = JobKey.jobKey(id, DEFAULT_GROUP_ID)
        try {
            scheduler.interrupt(jobKey)
            logger.info("Success to interrupt job[$jobKey]")
        } catch (exception: UnableToInterruptJobException) {
            logger.error("Failed to interrupt job[$id]", exception)
        }
    }

    fun deleteJob(id: String) {
        val jobKey = JobKey.jobKey(id, DEFAULT_GROUP_ID)
        try {
            interruptJob(id)
            scheduler.deleteJob(jobKey)
            logger.info("Success to delete job[$jobKey]")
        } catch (exception: SchedulerException) {
            logger.error("Failed to delete job[$id]", exception)
        }
    }

    fun checkExists(id: String): Boolean {
        return try {
            scheduler.checkExists(JobKey.jobKey(id, DEFAULT_GROUP_ID))
        } catch (exception: SchedulerException) {
            logger.error("Failed to check exist job[$id].", exception)
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduleService::class.java)
    }
}
