package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.schedule.JobRegistrar
import com.tencent.bkrepo.job.schedule.Registration
import com.tencent.devops.schedule.config.ScheduleWorkerProperties
import com.tencent.devops.schedule.config.ScheduleWorkerRpcAutoConfiguration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class JobWorkerConfiguration {

    @Bean
    fun jobRegistrar(
        @Qualifier(ScheduleWorkerRpcAutoConfiguration.SERVER_REST_TEMPLATE)
        workerRestTemplate: RestTemplate,
        workerProperties: ScheduleWorkerProperties,
        beanFactory: ConfigurableListableBeanFactory,
    ): JobRegistrar {
        return DevOpsScheduleJobRegistrar(workerRestTemplate, workerProperties, beanFactory)
    }

    @Bean
    fun registration(
        jobs: List<BatchJob<*>>,
        jobRegistrar: JobRegistrar,
    ): Registration {
        require(jobRegistrar is DevOpsScheduleJobRegistrar)
        return DevOpsScheduleAutoRegistration.registration(jobs, jobRegistrar)
    }
}
