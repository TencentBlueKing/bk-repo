package com.tencent.bkrepo.common.service.otel

import com.mongodb.MongoClientSettings
import com.mongodb.reactivestreams.client.internal.MongoClientImpl
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.otel.bridge.OtelTracer
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.observability.ContextProviderFactory
import org.springframework.data.mongodb.observability.MongoObservationCommandListener

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = ["management.tracing.enabled"])
@ConditionalOnClass(OtelTracer::class, MongoClientImpl::class)
class OtelMongoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun mongoMetricsSynchronousContextProvider(registry: ObservationRegistry): MongoClientSettingsBuilderCustomizer {
        return MongoClientSettingsBuilderCustomizer { clientSettingsBuilder: MongoClientSettings.Builder? ->
            clientSettingsBuilder!!.contextProvider(ContextProviderFactory.create(registry))
                .addCommandListener(MongoObservationCommandListener(registry))
        }
    }
}
