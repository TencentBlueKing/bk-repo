package com.tencent.bkrepo.common.service.async

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurerSupport
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy

@Configuration
class AsyncConfiguration : AsyncConfigurerSupport() {

    @Bean("artifactAsyncExecutor")
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = corePoolSize
        executor.maxPoolSize = maxPoolSize
        executor.setQueueCapacity(queueCapacity)
        executor.keepAliveSeconds = keepAliveSeconds
        executor.setThreadNamePrefix(threadNamePrefix)
        executor.setRejectedExecutionHandler(CallerRunsPolicy())
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler() = AsyncUncaughtExceptionHandler {
            error, method, _ -> logger.error("Unexpected exception occurred invoking async method: {}", method, error)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AsyncConfiguration::class.java)
        private const val corePoolSize = 2
        private const val maxPoolSize = 50
        private const val queueCapacity = 10000
        private const val keepAliveSeconds = 300
        private const val threadNamePrefix = "artifact-async-executor-"
    }
}
