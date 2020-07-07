package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
import com.tencent.bkrepo.replication.constant.TASK_ID_KEY
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.repository.LockRepository
import com.tencent.bkrepo.replication.repository.TaskRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.joda.time.DateTime
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RescheduleOnceJob {

    @Autowired
    private lateinit var scheduler: Scheduler

    @Autowired
    private lateinit var lockRepository: LockRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Scheduled(initialDelay = 1000 * 40, fixedDelay = Long.MAX_VALUE)
    @SchedulerLock(name = "reSchedule", lockAtMostFor = "PT1H")
    fun reSchedule() {
        scheduler.getJobKeys(GroupMatcher.groupEquals(DEFAULT_GROUP_ID)).iterator().forEach {
            logger.info("get jobs  key [${it.group}], [${it.name}]")
            val jobDetail = scheduler.getJobDetail(JobKey.jobKey(it.name, it.group))
            val taskId = jobDetail.jobDataMap.getString(TASK_ID_KEY)
            val task = taskRepository.findByIdOrNull(taskId) ?: run {
                logger.warn("Task[$taskId] does not exist.")
                return
            }

            // reschedule the job in replicating status and run once
            if (task.status == ReplicationStatus.REPLICATING && task.setting.executionPlan.executeImmediately) {
                lockRepository.deleteByTypeAndKeyNameAndKeyGroup("t", it.name, it.group)
                lockRepository.deleteByTypeAndKeyNameAndKeyGroup("j", it.name, it.group)
                val date = DateTime.now().plusSeconds(10).toDate()
                val trigger = TriggerBuilder.newTrigger()
                    .withIdentity(taskId, DEFAULT_GROUP_ID)
                    .startAt(date)
                    .build()
                task.replicationProgress.conflictedNode = 0L
                task.replicationProgress.failedNode = 0L
                task.replicationProgress.failedProject = 0L
                task.replicationProgress.failedRepo = 0L
                task.replicationProgress.replicatedNode = 0L
                task.replicationProgress.replicatedProject = 0L
                task.replicationProgress.replicatedRepo = 0L
                task.replicationProgress.successNode = 0L
                task.replicationProgress.successRepo = 0L
                task.replicationProgress.successProject = 0L
                taskRepository.save(task)
                scheduler.rescheduleJob(TriggerKey.triggerKey(it.name, it.group), trigger)
                logger.info("job  key [${it.group}], [${it.name}] reschedule")
            }
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
