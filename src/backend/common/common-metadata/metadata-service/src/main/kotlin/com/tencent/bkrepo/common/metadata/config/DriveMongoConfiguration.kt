package com.tencent.bkrepo.common.metadata.config

import com.mongodb.MongoClientSettings
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.mongo.MongoAutoConfiguration
import com.tencent.bkrepo.common.mongo.api.properties.MongoConnectionPoolProperties
import com.tencent.bkrepo.common.mongo.api.properties.MongoSslProperties
import com.tencent.bkrepo.common.service.otel.mongodb.OtelMongoConfiguration
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsCommandListener
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoClientFactory
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.autoconfigure.mongo.PropertiesMongoConnectionDetails
import org.springframework.boot.autoconfigure.mongo.StandardMongoClientSettingsBuilderCustomizer
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.ssl.SslBundles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter

/**
 * BKDrive 独立数据库同步访问配置。
 *
 * 通过 spring.data.mongodb.drive 指定连接；未配置时回落到默认 mongoTemplate。
 * 须在默认 mongoTemplate 注册之后再加载，且仅在 mongoTemplate 已存在时注册 driveMongoTemplate，
 * 避免 bean 定义提前注册导致 MongoDataAutoConfiguration 被跳过。
 */
@AutoConfiguration(after = [MongoAutoConfiguration::class, MongoDataAutoConfiguration::class])
@Conditional(SyncCondition::class)
class DriveMongoConfiguration {

    @Bean(name = ["driveMongoTemplate"])
    @ConditionalOnBean(name = ["mongoTemplate"])
    fun driveMongoTemplate(
        mongoTemplate: MongoTemplate,
        environment: Environment,
        converter: MappingMongoConverter?,
        sslBundles: ObjectProvider<SslBundles>,
        sslProperties: MongoSslProperties,
        mongoConnectionPoolProperties: MongoConnectionPoolProperties,
        mongoMetricsConnectionPoolListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
        mongoMetricsCommandListener: ObjectProvider<MongoMetricsCommandListener>,
        registry: ObservationRegistry,
    ): MongoTemplate {
        val driveProps = driveMongoProperties(environment)
        if (driveProps == null || driveProps.determineUri() == MongoProperties.DEFAULT_URI) {
            return mongoTemplate
        }
        val driveMongoDatabaseFactory = createMongoDatabaseFactory(
            properties = driveProps,
            sslBundles = sslBundles,
            sslProperties = sslProperties,
            mongoConnectionPoolProperties = mongoConnectionPoolProperties,
            mongoMetricsConnectionPoolListener = mongoMetricsConnectionPoolListener,
            mongoMetricsCommandListener = mongoMetricsCommandListener,
            registry = registry,
        )
        return MongoTemplate(driveMongoDatabaseFactory, converter)
    }

    private fun createMongoDatabaseFactory(
        properties: MongoProperties,
        sslBundles: ObjectProvider<SslBundles>,
        sslProperties: MongoSslProperties,
        mongoConnectionPoolProperties: MongoConnectionPoolProperties,
        mongoMetricsConnectionPoolListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
        mongoMetricsCommandListener: ObjectProvider<MongoMetricsCommandListener>,
        registry: ObservationRegistry,
    ): MongoDatabaseFactory {
        val settingsBuilder = MongoClientSettings.builder()
        StandardMongoClientSettingsBuilderCustomizer(
            PropertiesMongoConnectionDetails(properties).getConnectionString(),
            properties.uuidRepresentation,
            properties.ssl,
            sslBundles.getIfAvailable(),
        ).customize(settingsBuilder)
        MongoAutoConfiguration
            .MongoClientCustomizer(sslProperties, mongoConnectionPoolProperties)
            .customize(settingsBuilder)
        OtelMongoConfiguration.OtelMongoCustomizer(registry).customize(settingsBuilder)
        mongoMetricsConnectionPoolListener.ifAvailable?.let { listener ->
            settingsBuilder.applyToConnectionPoolSettings { it.addConnectionPoolListener(listener) }
        }
        mongoMetricsCommandListener.ifAvailable?.let { listener ->
            settingsBuilder.addCommandListener(listener)
        }
        val client = MongoClientFactory(emptyList()).createMongoClient(settingsBuilder.build())
        val database = properties.database ?: PropertiesMongoConnectionDetails(properties).connectionString.database
        return SimpleMongoClientDatabaseFactory(client, database)
    }

    private fun driveMongoProperties(environment: Environment): MongoProperties? {
        return Binder.get(environment)
            .bind("spring.data.mongodb.drive", MongoProperties::class.java)
            .orElseGet { null }
    }
}
