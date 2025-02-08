package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.schedule.JobRegistrar
import com.tencent.bkrepo.job.schedule.Registration
import com.tencent.bkrepo.job.worker.rpc.JobRpcClient
import com.tencent.devops.schedule.config.ScheduleWorkerProperties
import com.tencent.devops.schedule.config.ScheduleWorkerRpcAutoConfiguration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class JobWorkerConfiguration {

    @Value("\${job.worker.prefix:$DEFAULT_PREFIX}")
    private val prefix: String = ""

    @Bean
    fun jobRegistrar(
        jobRpcClient: JobRpcClient,
        beanFactory: ConfigurableListableBeanFactory,
    ) = DevOpsScheduleJobRegistrar(jobRpcClient, beanFactory, prefix)

    @Bean
    fun registration(
        jobs: List<BatchJob<*>>,
        jobRegistrar: JobRegistrar,
    ): Registration {
        require(jobRegistrar is DevOpsScheduleJobRegistrar)
        return DevOpsScheduleAutoRegistration.registration(jobs, jobRegistrar, prefix)
    }

    @Bean
    fun jobClient(
        @Qualifier(ScheduleWorkerRpcAutoConfiguration.SERVER_REST_TEMPLATE)
        workerRestTemplate: RestTemplate,
        workerProperties: ScheduleWorkerProperties,
    ) = JobRpcClient(workerRestTemplate, workerProperties.server.address)

    companion object {
        private const val DEFAULT_PREFIX = "SYSTEM_JOB_"
    }
}
