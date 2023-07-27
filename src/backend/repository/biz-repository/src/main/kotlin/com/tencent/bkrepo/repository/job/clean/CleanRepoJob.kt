package com.tencent.bkrepo.repository.job.clean

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.quartz.InterruptableJob
import org.quartz.JobExecutionContext
import java.time.Duration

class CleanRepoJob : InterruptableJob {
    private var currentThread: Thread? = null
    private val lockingTaskExecutor = SpringContextUtils.getBean(LockingTaskExecutor::class.java)
    private val cleanRepoJobExecutor = SpringContextUtils.getBean(CleanRepoJobExecutor::class.java)

    override fun execute(context: JobExecutionContext) {
        currentThread = Thread.currentThread()
        val taskId = context.jobDetail.key.name
        val lockName = buildLockName(taskId)
        val lockConfiguration = LockConfiguration(lockName, lockAtMostFor, lockAtLeastFor)
        lockingTaskExecutor.executeWithLock(Runnable { cleanRepoJobExecutor.execute(taskId) },lockConfiguration)
    }

    override fun interrupt() {
        currentThread?.interrupt()
    }

    private fun buildLockName(taskId: String): String {
        return CLEAN_LOCK_NAME_PREFIX + taskId
    }

    companion object {
        /**
         * 任务最短加锁时间
         */
        private val lockAtLeastFor = Duration.ofSeconds(1)

        /**
         * 任务最长加锁时间
         */
        private val lockAtMostFor = Duration.ofDays(1)

        private const val CLEAN_LOCK_NAME_PREFIX = "CLEAN_JOB_"
    }
}
