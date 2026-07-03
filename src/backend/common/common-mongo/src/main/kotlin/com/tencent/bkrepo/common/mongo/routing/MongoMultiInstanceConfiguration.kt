package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.mongodb.client.MongoClients
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

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
    private val syncStateDao: ObjectProvider<MigrationSyncStateDao>,
) {

    @Bean
    @RefreshScope
    fun mongoRoutingRegistry(): MongoRoutingRegistry {
        val hasActiveRules = properties.rules.any { it.value.routingState != RuleRoutingState.OFF }
        val core = if (hasActiveRules) {
            DefaultMongoRoutingRegistry(
                properties,
                syncStateDao.ifAvailable,
            ).also { it.validateOnStartup() }
        } else {
            StandardRoutingRegistry(defaultMongoTemplate, properties)
        }
        return metricsProvider.ifAvailable?.let { MetricsAwareMongoRoutingRegistry(core, it) } ?: core
    }

    @Bean("compensationMongoTemplate")
    @ConditionalOnProperty(
        prefix = "spring.data.mongodb.multi-instance.compensation",
        name = ["storage-uri"],
    )
    fun compensationMongoTemplate(): MongoTemplate {
        val uri = properties.compensation.storageUri
        require(uri.isNotBlank()) { "compensation.storage-uri must not be blank" }
        val connectionString = com.mongodb.ConnectionString(uri)
        val client = MongoClients.create(connectionString)
        compensationClients.add(client)
        val database = connectionString.database
            ?: error("compensation.storage-uri must include database name")
        return MongoTemplate(SimpleMongoClientDatabaseFactory(client, database))
    }

    @jakarta.annotation.PreDestroy
    fun shutdownCompensationClients() {
        MongoClientShutdownHandler.closeAll(compensationClients)
        compensationClients.clear()
    }

    private val compensationClients = mutableListOf<com.mongodb.client.MongoClient>()
}
