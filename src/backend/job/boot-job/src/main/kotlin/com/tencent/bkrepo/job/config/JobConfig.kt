package com.tencent.bkrepo.job.config

import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.batch.FileReferenceCleanupJob
import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Job配置
 * */
@Configuration
@EnableConfigurationProperties(JobProperties::class)
class JobConfig {
    @Bean
    fun blockThreadPoolTaskExecutorDecorator(
        threadPoolTaskExecutor: ThreadPoolTaskExecutor,
        properties: TaskExecutionProperties
    ): BlockThreadPoolTaskExecutorDecorator {
        return BlockThreadPoolTaskExecutorDecorator(
            threadPoolTaskExecutor,
            properties.pool.queueCapacity,
            Runtime.getRuntime().availableProcessors()
        )
    }

    @Bean
    fun fileReferenceCleanupJob(
        storageService: StorageService,
        mongoTemplate: MongoTemplate,
        storageCredentialsClient: StorageCredentialsClient,
        jobProperties: JobProperties
    ): FileReferenceCleanupJob {
        return FileReferenceCleanupJob(
            storageService,
            mongoTemplate,
            storageCredentialsClient,
            jobProperties.fileReferenceCleanupJobProperties
        )
    }
}
