package com.tencent.bkrepo.job.schedule

import com.tencent.bkrepo.common.service.shutdown.ServiceShutdownHook
import com.tencent.bkrepo.job.batch.base.BatchJob
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * 自动注册job到Spring的调度中
 * */
class SpringScheduleAutoRegistration(
    private val jobs: List<BatchJob<*>>,
    private val jobRegistrar: SpringScheduleJobRegistrar,
) : SchedulingConfigurer, Registration {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        jobRegistrar.taskRegistrar = taskRegistrar
        jobRegistrar.init()
        ServiceShutdownHook.add {
            jobRegistrar.unload()
            jobs.forEach { it.stop(it.batchJobProperties.stopTimeout, true) }
        }
        this.configureJobs(jobs)
    }

    override fun configureJobs(jobs: List<BatchJob<*>>) {
        jobs.filter { it.batchJobProperties.enabled }.forEach {
            jobRegistrar.register(JobUtils.parseBatchJob(it))
        }
    }

    companion object {
        fun registration(
            jobs: List<BatchJob<*>>,
            jobRegistrar: SpringScheduleJobRegistrar,
        ): SpringScheduleAutoRegistration {
            return SpringScheduleAutoRegistration(jobs, jobRegistrar)
        }
    }
}
