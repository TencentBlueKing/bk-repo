package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
@EnableConfigurationProperties(MongoMultiInstanceProperties::class)
@ConditionalOnProperty(
    prefix = "spring.data.mongodb.multi-instance",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class MongoMultiInstanceConfiguration(
    private val properties: MongoMultiInstanceProperties,
    private val defaultMongoTemplate: MongoTemplate,
    private val metricsProvider: ObjectProvider<MongoRoutingMetrics>,
    private val poolMetricsListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
) {

    @Bean
    @RefreshScope
    fun mongoRoutingRegistry(): MongoRoutingRegistry {
        val hasActiveRules = properties.rules.any { it.value.routingState != RuleRoutingState.OFF }
        val core = if (hasActiveRules) {
            DefaultMongoRoutingRegistry(
                properties,
                poolMetricsListener.ifAvailable,
            ).also { it.validateOnStartup() }
        } else {
            StandardRoutingRegistry(defaultMongoTemplate, properties)
        }
        return metricsProvider.ifAvailable?.let { MetricsAwareMongoRoutingRegistry(core, it) } ?: core
    }
}


