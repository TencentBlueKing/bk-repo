package com.tencent.bkrepo.common.service.async

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurerSupport
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AsyncProperties::class)
@Configuration
class AsyncConfiguration : AsyncConfigurerSupport() {

    @Autowired
    private lateinit var properties: AsyncProperties

    /**
     * Spring异步任务Executor
     */
    @Bean("taskAsyncExecutor")
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = properties.corePoolSize
        executor.maxPoolSize = properties.maxPoolSize
        executor.setQueueCapacity(properties.queueCapacity)
        executor.keepAliveSeconds = properties.keepAliveSeconds
        executor.setThreadNamePrefix(properties.threadNamePrefix)
        executor.setRejectedExecutionHandler(CallerRunsPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(DEFAULT_AWAIT_TERMINATION_SECONDS)
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler() = AsyncUncaughtExceptionHandler {
            error, method, _ -> logger.error("Unexpected exception occurred invoking async method: {}", method, error)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AsyncConfiguration::class.java)
        private val DEFAULT_AWAIT_TERMINATION_SECONDS = 5 * 60
    }
}
