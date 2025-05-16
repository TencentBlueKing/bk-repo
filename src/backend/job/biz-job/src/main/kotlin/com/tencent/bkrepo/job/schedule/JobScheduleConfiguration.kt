package com.tencent.bkrepo.job.schedule

import com.tencent.bkrepo.job.batch.base.BatchJob
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 任务调度配置
 * */
@Configuration
class JobScheduleConfiguration {
    @Bean
    @ConditionalOnMissingBean(JobRegistrar::class)
    fun jobRegistrar(builder: ThreadPoolTaskSchedulerBuilder): JobRegistrar {
        return SpringScheduleJobRegistrar(builder)
    }

    @Bean
    @ConditionalOnMissingBean(Registration::class)
    fun registration(
        jobs: List<BatchJob<*>>,
        jobRegistrar: JobRegistrar,
    ): Registration {
        require(jobRegistrar is SpringScheduleJobRegistrar)
        return SpringScheduleAutoRegistration.registration(jobs, jobRegistrar)
    }
}
