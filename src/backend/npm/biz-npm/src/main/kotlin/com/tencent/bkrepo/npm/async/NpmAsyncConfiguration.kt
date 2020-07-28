package com.tencent.bkrepo.npm.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy

@EnableConfigurationProperties(NpmAsyncProperties::class)
@Configuration
class NpmAsyncConfiguration {

    @Autowired
    private lateinit var properties: NpmAsyncProperties

    @Bean("npmTaskAsyncExecutor")
    fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = properties.corePoolSize
        executor.maxPoolSize = properties.maxPoolSize
        executor.setQueueCapacity(properties.queueCapacity)
        executor.keepAliveSeconds = properties.keepAliveSeconds
        executor.setThreadNamePrefix(properties.threadNamePrefix)
        executor.setRejectedExecutionHandler(CallerRunsPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
        executor.initialize()
        return executor
    }

    companion object {
        const val AWAIT_TERMINATION_SECONDS = 5 * 60
    }
}
