package com.tencent.bkrepo.common.mongo.routing

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.stereotype.Component
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry

/**
 * G-43：散发查询专用 MongoClient/连接池，与业务读写池隔离。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class ScatterMongoTemplateProvider(
    private val properties: MongoMultiInstanceProperties,
    @Value("\${spring.data.mongodb.uri}")
    private val defaultUri: String,
) : DisposableBean {

    private val clients = mutableListOf<MongoClient>()
    private val scatterConfig = properties.scatterQuery

    private val defaultScatterTemplate: MongoTemplate by lazy {
        templateForUri(defaultUri, "scatter-default")
    }

    private val heavyScatterTemplates: Map<String, MongoTemplate> by lazy {
        val rule = properties.rules[NODE_RULE] ?: return@lazy emptyMap()
        rule.instances.mapValues { (_, cfg) ->
            val uri = cfg.uri
            templateForUri(uri, "scatter-${cfg.uri.hashCode()}")
        }
    }

    fun defaultTemplate(): MongoTemplate = defaultScatterTemplate

    fun heavyTemplate(instanceName: String): MongoTemplate? = heavyScatterTemplates[instanceName]

    override fun destroy() {
        MongoClientShutdownHandler.closeAll(clients)
        clients.clear()
    }

    private fun templateForUri(uri: String, clientName: String): MongoTemplate {
        val connectionString = ConnectionString(uri)
        val settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings { builder ->
                builder
                    .maxSize(scatterConfig.dedicatedMaxPoolSize)
                    .minSize(scatterConfig.dedicatedMinPoolSize)
            }
            .applicationName(clientName)
            .build()
        val client = MongoClients.create(settings)
        clients.add(client)
        val database = connectionString.database
            ?: error("MongoDB URI must include database: $uri")
        return MongoTemplate(SimpleMongoClientDatabaseFactory(client, database))
    }

    companion object {
        private const val NODE_RULE = "node"
    }
}
