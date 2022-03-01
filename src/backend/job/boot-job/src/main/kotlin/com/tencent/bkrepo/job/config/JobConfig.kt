package com.tencent.bkrepo.job.config

import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Job配置
 * */
@Configuration
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
}
