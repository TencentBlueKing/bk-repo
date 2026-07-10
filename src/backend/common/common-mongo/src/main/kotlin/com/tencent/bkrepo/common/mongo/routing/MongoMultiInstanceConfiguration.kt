package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter

@Configuration
@Import(MongoMigrationDaoConfiguration::class)
@EnableConfigurationProperties(MongoMultiInstanceProperties::class)
class MongoMultiInstanceConfiguration(
    private val properties: MongoMultiInstanceProperties,
    private val defaultMongoTemplate: MongoTemplate,
    private val mongoConverter: MongoConverter,
    private val metricsProvider: ObjectProvider<MongoRoutingMetrics>,
    private val poolMetricsListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
    private val meterRegistryProvider: ObjectProvider<MeterRegistry>,
) {

    @Bean
    fun mongoRoutingRegistry(): MongoRoutingRegistry {
        val core = if (properties.rules.isEmpty()) {
            StandardRoutingRegistry(defaultMongoTemplate, properties)
        } else {
            DefaultMongoRoutingRegistry(properties, mongoConverter, poolMetricsListener.ifAvailable)
                .also { it.validateOnStartup() }
        }
        return metricsProvider.ifAvailable?.let { MetricsAwareMongoRoutingRegistry(core, it) } ?: core
    }

    @Bean
    fun compensationPostCheck(
        registry: MongoRoutingRegistry,
    ): CompensationPostCheck? {
        val meterRegistry = meterRegistryProvider.ifAvailable ?: return null
        return CompensationPostCheck(registry, defaultMongoTemplate, meterRegistry)
    }

    @Bean
    fun mongoDualWriteCompensationService(
        registry: MongoRoutingRegistry,
        postCheck: ObjectProvider<CompensationPostCheck>,
        routingMetrics: ObjectProvider<MongoRoutingMetrics>,
    ): MongoDualWriteCompensationService = MongoDualWriteCompensationService(
        mongoTemplate = defaultMongoTemplate,
        mongoConverter = mongoConverter,
        routingRegistry = registry,
        properties = properties,
        postCheck = postCheck.ifAvailable,
        routingMetrics = routingMetrics.ifAvailable,
    )

    @Bean
    fun compensationHealthChecker(
        compensationService: MongoDualWriteCompensationService,
    ): CompensationHealthChecker = CompensationHealthChecker(compensationService)

    @Bean
    fun dualWriteSidecarVerifier(
        registry: MongoRoutingRegistry,
        routingMetrics: ObjectProvider<MongoRoutingMetrics>,
    ): DualWriteSidecarVerifier = DualWriteSidecarVerifier(
        defaultMongoTemplate,
        registry,
        routingMetrics.ifAvailable,
    )

    @Bean
    fun migrationGate(
        registry: MongoRoutingRegistry,
        syncStateDao: ObjectProvider<MigrationSyncStateDao>,
    ): MigrationGate = MigrationGate(registry, properties, syncStateDao.ifAvailable)
}