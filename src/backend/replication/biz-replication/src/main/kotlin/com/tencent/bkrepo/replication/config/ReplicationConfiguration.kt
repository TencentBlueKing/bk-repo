package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import org.springframework.cloud.openfeign.FeignClientsConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(FeignClientsConfiguration::class)
class ReplicationConfiguration: ArtifactConfiguration {

    @Bean
    fun scheduler(): Scheduler {
        return StdSchedulerFactory.getDefaultScheduler().apply { start() }
    }
}
