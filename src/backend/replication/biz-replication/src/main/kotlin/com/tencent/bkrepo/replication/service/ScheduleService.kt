package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
import com.tencent.bkrepo.replication.constant.TASK_ID_KEY
import com.tencent.bkrepo.replication.job.FullReplicaJob
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.ReplicationType
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
    fun createJob(task: TReplicaTask) {
        when (task.type) {
            ReplicationType.FULL -> createFullReplicaJob(task)
            ReplicationType.INCREMENTAL -> createIncrementalReplicaJob(task)
        }
    }

    private fun createFullReplicaJob(task: TReplicaTask) {
        val jobDetail = JobBuilder.newJob(FullReplicaJob::class.java)
            .withIdentity(task.id, DEFAULT_GROUP_ID)
            .usingJobData(TASK_ID_KEY, task.id)
            .build()
        val trigger = createTrigger(task)
        scheduler.scheduleJob(jobDetail, trigger)
        logger.info("Create full replica job success!")
    }

    private fun createTrigger(task: TReplicaTask): Trigger {
        with(task.setting.executionPlan) {
            return when {
                executeImmediately -> {
                    TriggerBuilder.newTrigger()
                        .withIdentity(task.id, DEFAULT_GROUP_ID)
                        .startNow()
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

    private fun createIncrementalReplicaJob(task: TReplicaTask) {
        TODO("IncrementalReplicaJob")
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
