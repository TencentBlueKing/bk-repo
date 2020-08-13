package com.tencent.bkrepo.common.artifact.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(ArtifactMetrics::class)
class ArtifactMetricsConfiguration {

    @Bean
    fun metricsCommonTags(registration: Registration): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry: MeterRegistry ->
            registry.config().commonTags("service", registration.serviceId)
                .commonTags("instance", "${registration.host}-${registration.instanceId}")
        }
    }
}
