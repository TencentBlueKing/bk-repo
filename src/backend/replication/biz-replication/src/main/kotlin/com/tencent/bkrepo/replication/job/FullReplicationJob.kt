package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.replication.constant.TASK_ID_KEY
import com.tencent.bkrepo.replication.handler.full.FullJobHandler
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.repository.TaskRepository
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.quartz.PersistJobDataAfterExecution
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.quartz.QuartzJobBean
import java.time.Duration
import java.time.LocalDateTime

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class FullReplicationJob : QuartzJobBean() {

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var fullJobHandler: FullJobHandler

    override fun executeInternal(context: JobExecutionContext) {
        fullJobHandler.updateTriggerStatus(context.trigger.key.name, context.trigger.key.group)
        val taskId = context.jobDetail.jobDataMap.getString(TASK_ID_KEY)
        logger.info("start to execute replication task[$taskId].")
        val task = taskRepository.findByIdOrNull(taskId) ?: run {
            logger.error("task[$taskId] does not exist.")
            return
        }
        if (task.status == ReplicationStatus.PAUSED) {
            logger.info("task[$taskId] status is paused, skip task.")
        }
        try {
            val replicaContext = ReplicationContext(task)
            // 更新状态
            task.status = ReplicationStatus.REPLICATING
            task.startTime = LocalDateTime.now()
            // 检查版本
            fullJobHandler.checkVersion(replicaContext)
            // 准备同步详情信息
            fullJobHandler.prepare(replicaContext)
            // 更新task
            taskRepository.save(task)
            // 开始同步
            fullJobHandler.startReplica(replicaContext)
            // 更新状态
            task.status = ReplicationStatus.SUCCESS
        } catch (exception: Exception) {
            // 记录异常
            task.status = ReplicationStatus.FAILED
            task.errorReason = exception.message
        } finally {
            // 保存结果
            task.endTime = LocalDateTime.now()
            taskRepository.save(task)
            val consumeSeconds = Duration.between(task.startTime!!, task.endTime!!).seconds
            logger.info("Replica task[$taskId] finished[${task.status}], reason[${task.errorReason}], consume [$consumeSeconds]s.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FullReplicationJob::class.java)
    }
}
