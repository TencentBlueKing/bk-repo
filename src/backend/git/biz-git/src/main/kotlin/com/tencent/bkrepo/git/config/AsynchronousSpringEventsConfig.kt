package com.tencent.bkrepo.git.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AsynchronousSpringEventsConfig {

    @Bean
    fun applicationEventMulticaster(taskExecutor: ThreadPoolTaskExecutor): ApplicationEventMulticaster {
        val eventMulticaster = SimpleApplicationEventMulticaster()
        eventMulticaster.setTaskExecutor(taskExecutor)
        return eventMulticaster
    }
}
