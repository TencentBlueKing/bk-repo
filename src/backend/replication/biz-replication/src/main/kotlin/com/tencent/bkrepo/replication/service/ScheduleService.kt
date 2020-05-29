package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
import com.tencent.bkrepo.replication.constant.TASK_ID_KEY
import com.tencent.bkrepo.replication.job.FullReplicationJob
import com.tencent.bkrepo.replication.model.TReplicationTask
import org.joda.time.DateTime
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.util.Date

@Service
class ScheduleService @Autowired constructor(
    private val scheduler: Scheduler
) {
    fun createJob(task: TReplicationTask) {
        createFullReplicaJob(task)
    }

    private fun createFullReplicaJob(task: TReplicationTask) {
        val jobDetail = JobBuilder.newJob(FullReplicationJob::class.java)
            .withIdentity(task.id, DEFAULT_GROUP_ID)
            .usingJobData(TASK_ID_KEY, task.id)
            .requestRecovery()
            .build()
        val trigger = createTrigger(task)
        scheduler.scheduleJob(jobDetail, trigger)
        logger.info("Create full replication job success!")
    }

    private fun createTrigger(task: TReplicationTask): Trigger {
        with(task.setting.executionPlan) {
            return when {
                executeImmediately -> {
                    val date = DateTime.now().plusSeconds(10).toDate()
                    TriggerBuilder.newTrigger()
                        .withIdentity(task.id, DEFAULT_GROUP_ID)
                        .startAt(date)
                        .build()
                }
                executeTime != null -> {
                    TriggerBuilder.newTrigger()
                        .withIdentity(task.id, DEFAULT_GROUP_ID)
                        .startAt(Date.from(executeTime!!.atZone(ZoneId.systemDefault()).toInstant()))
                        .build()
                }
                else -> {
                    TriggerBuilder.newTrigger()
                        .withIdentity(task.id, DEFAULT_GROUP_ID)
                        .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                        .build()
                }
            }
        }
    }

    fun pauseJob(id: String) {
        scheduler.pauseJob(JobKey.jobKey(id, DEFAULT_GROUP_ID))
    }

    fun resumeJob(id: String) {
        scheduler.resumeJob(JobKey.jobKey(id, DEFAULT_GROUP_ID))
    }

    fun deleteJob(id: String) {
        scheduler.deleteJob(JobKey.jobKey(id, DEFAULT_GROUP_ID))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduleService::class.java)
    }
}
