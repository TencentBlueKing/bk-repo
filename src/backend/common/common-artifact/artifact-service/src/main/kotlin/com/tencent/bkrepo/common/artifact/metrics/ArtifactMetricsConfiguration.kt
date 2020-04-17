package com.tencent.bkrepo.common.artifact.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    ArtifactMetrics::class,
    ActuatorAuthConfiguration::class
)
class ArtifactMetricsConfiguration {

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry: MeterRegistry ->
            registry.config().commonTags("application", applicationName)
        }
    }
}