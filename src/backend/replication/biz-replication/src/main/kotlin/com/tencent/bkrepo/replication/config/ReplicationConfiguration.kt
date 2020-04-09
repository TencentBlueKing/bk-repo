package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import org.springframework.cloud.openfeign.FeignClientsConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(FeignClientsConfiguration::class)
class ReplicationConfiguration {
    @Bean
    fun artifactConfiguration() = object : ArtifactConfiguration {}
}
