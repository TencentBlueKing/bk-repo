package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.constant.TASK_ID
import com.tencent.bkrepo.replication.handler.job.ScheduleJobHandler
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.quartz.InterruptableJob
import org.quartz.JobExecutionContext
import java.time.Duration

class ReplicationQuartzJob : InterruptableJob {

    private lateinit var currentThread: Thread
    private val lockingTaskExecutor = SpringContextUtils.getBean(LockingTaskExecutor::class.java)
    private val replicationJobBean = SpringContextUtils.getBean(ScheduleJobHandler::class.java)

    override fun execute(context: JobExecutionContext) {
        currentThread = Thread.currentThread()
        val taskId = context.jobDetail.jobDataMap.getString(TASK_ID)
        val lockConfiguration = LockConfiguration("ReplicationJob$taskId", lockAtMostFor, lockAtLeastFor)
        lockingTaskExecutor.executeWithLock(Runnable { replicationJobBean.execute(taskId) }, lockConfiguration)
    }

    override fun interrupt() {
        currentThread.interrupt()
    }

    companion object {
        private val lockAtLeastFor = Duration.ofSeconds(1)
        private val lockAtMostFor = Duration.ofDays(1)
    }
}
