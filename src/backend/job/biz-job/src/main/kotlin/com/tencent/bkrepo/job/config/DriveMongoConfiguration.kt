package com.tencent.bkrepo.job.config

import com.mongodb.MongoClientSettings
import com.tencent.bkrepo.common.mongo.MongoAutoConfiguration
import com.tencent.bkrepo.common.mongo.api.properties.MongoConnectionPoolProperties
import com.tencent.bkrepo.common.mongo.api.properties.MongoSslProperties
import com.tencent.bkrepo.common.service.otel.mongodb.OtelMongoConfiguration
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsCommandListener
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.mongo.MongoClientFactory
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.autoconfigure.mongo.PropertiesMongoConnectionDetails
import org.springframework.boot.autoconfigure.mongo.StandardMongoClientSettingsBuilderCustomizer
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.ssl.SslBundles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter

/**
 * job 模块 BKDrive 独立数据库配置。
 *
 * 通过 spring.data.mongodb.drive 配置 drive 专用连接，
 * 未配置时回落到默认 mongoTemplate，保持兼容。
 */
@Configuration
class DriveMongoConfiguration {

    @Bean
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
        // standard
        StandardMongoClientSettingsBuilderCustomizer(
            PropertiesMongoConnectionDetails(properties).getConnectionString(),
            properties.uuidRepresentation,
            properties.ssl,
            sslBundles.getIfAvailable()
        ).customize(settingsBuilder)

        // custom
        MongoAutoConfiguration
            .MongoClientCustomizer(sslProperties, mongoConnectionPoolProperties)
            .customize(settingsBuilder)

        // otel
        OtelMongoConfiguration.OtelMongoCustomizer(registry).customize(settingsBuilder)

        // metrics
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
