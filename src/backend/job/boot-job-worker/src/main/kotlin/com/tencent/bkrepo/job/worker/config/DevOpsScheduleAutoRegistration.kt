package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.schedule.Job
import com.tencent.bkrepo.job.schedule.JobUtils
import com.tencent.bkrepo.job.schedule.Registration
import com.tencent.bkrepo.job.worker.config.DevOpsScheduleJobRegistrar.Companion.SYSTEM_JOB_PREFIX
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener

/**
 * 自动注册任务到调度中心
 * */
class DevOpsScheduleAutoRegistration(
    private val jobs: List<BatchJob<*>>,
    private val jobRegistrar: DevOpsScheduleJobRegistrar,
) : Registration, ApplicationListener<ApplicationReadyEvent> {
    override fun configureJobs(jobs: List<BatchJob<*>>) {
        val newOrUpdateJobs = checkUpdates(jobRegistrar.list(), jobs).map { it.getJobName() }
        jobs.filter { it.batchJobProperties.enabledV2 }
            .map { JobUtils.parseBatchJob(it) }
            .forEach {
                jobRegistrar.registerJobHandlerBean(it)
                if (newOrUpdateJobs.contains(it.name)) {
                    jobRegistrar.register(it)
                }
            }
    }

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        jobRegistrar.init()
        configureJobs(jobs)
    }

    /**
     * 检测是否有任务更新
     * */
    private fun checkUpdates(jobs: List<Job>, newJobs: List<BatchJob<*>>): List<BatchJob<*>> {
        val updates = mutableListOf<BatchJob<*>>()
        val jobMap = jobs.associateBy { it.name.removePrefix(SYSTEM_JOB_PREFIX) }
        val deletes = jobs.toMutableList()
        newJobs.forEach {
            val oldJob = jobMap[it.getJobName()]
            if (oldJob != null) {
                // 任务配置有变化，需要删除旧任务
                if (it.batchJobProperties.enabledV2 && hasChange(oldJob, it)) {
                    updates.add(it)
                } else {
                    deletes.remove(oldJob)
                }
            } else {
                // 新建任务
                updates.add(it)
            }
        }
        deletes.forEach {
            jobRegistrar.unregister(it)
        }
        return updates
    }

    /**
     * 任务配置是否有变更
     * */
    private fun hasChange(oldJob: Job, newJob: BatchJob<*>): Boolean {
        require(oldJob.name.removePrefix(SYSTEM_JOB_PREFIX) == newJob.getJobName())
        val job = JobUtils.parseBatchJob(newJob)
        return (job.group.isNotEmpty() && oldJob.group != job.group) ||
            oldJob.scheduleConf != job.scheduleConf
    }

    companion object {
        fun registration(
            jobs: List<BatchJob<*>>,
            jobRegistrar: DevOpsScheduleJobRegistrar,
        ): DevOpsScheduleAutoRegistration {
            return DevOpsScheduleAutoRegistration(jobs, jobRegistrar)
        }
    }
}
