package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.MongoAutoConfiguration
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter

@Configuration
@Import(MongoMigrationDaoConfiguration::class)
@EnableConfigurationProperties(MongoMultiInstanceProperties::class)
class MongoMultiInstanceConfiguration(
    private val properties: MongoMultiInstanceProperties,
    private val defaultMongoTemplate: MongoTemplate,
    private val mongoConverter: MongoConverter,
    private val mongoDatabaseFactory: MongoDatabaseFactory,
    private val metricsProvider: ObjectProvider<MongoRoutingMetrics>,
    private val poolMetricsListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
    private val meterRegistryProvider: ObjectProvider<MeterRegistry>,
) {

    @Bean
    fun mongoRoutingRegistry(): MongoRoutingRegistry {
        val core = if (properties.rules.isEmpty()) {
            StandardRoutingRegistry(defaultMongoTemplate, properties)
        } else {
            // 实例库模板必须使用独立的 MappingContext（autoIndexCreation=false），
            // 否则会把全量 @Document 实体的索引建到实例库上，见 MongoAutoConfiguration.createConverter
            val routingConverter = MongoAutoConfiguration.createConverter(mongoDatabaseFactory, false)
            DefaultMongoRoutingRegistry(properties, routingConverter, poolMetricsListener.ifAvailable)
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
        @Value("\${block-node.collection-name:block_node}") blockNodeCollectionBase: String,
    ): DualWriteSidecarVerifier = DualWriteSidecarVerifier(
        defaultMongoTemplate,
        registry,
        routingMetrics.ifAvailable,
        blockNodeCollectionBase = blockNodeCollectionBase.ifBlank {
            NodeReconciliationHelper.DEFAULT_BLOCK_NODE_BASE
        },
    )

    @Bean
    fun migrationGate(
        registry: MongoRoutingRegistry,
        syncStateDao: MigrationSyncStateDao,
    ): MigrationGate = MigrationGate(registry, properties, syncStateDao)
}