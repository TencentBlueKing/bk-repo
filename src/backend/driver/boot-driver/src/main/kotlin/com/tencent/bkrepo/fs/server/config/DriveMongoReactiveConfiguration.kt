package com.tencent.bkrepo.fs.server.config

import com.mongodb.MongoClientSettings
import com.mongodb.connection.TransportSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.tencent.bkrepo.common.mongo.MongoReactiveAutoConfiguration
import com.tencent.bkrepo.common.mongo.api.properties.MongoConnectionPoolProperties
import com.tencent.bkrepo.common.mongo.api.properties.MongoSslProperties
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsCommandListener
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.autoconfigure.mongo.PropertiesMongoConnectionDetails
import org.springframework.boot.autoconfigure.mongo.ReactiveMongoClientFactory
import org.springframework.boot.autoconfigure.mongo.StandardMongoClientSettingsBuilderCustomizer
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.ssl.SslBundles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import kotlin.concurrent.Volatile

/**
 * Drive 模块独立数据库配置
 *
 * 通过配置 spring.data.mongodb.drive 属性来指定独立的 MongoDB 数据库连接。
 * 如果未配置独立连接（uri 为默认值），则复用默认的 reactiveMongoTemplate。
 *
 * 参考MongoReactiveAutoConfiguration、MongoReactiveDataAutoConfiguration、MongoMetricsAutoConfiguration进行配置
 */
@Configuration
class DriveMongoReactiveConfiguration {

    @Volatile
    private var driveMongoEventLoopGroup: EventLoopGroup? = null

    @Bean
    fun driveReactiveMongoTemplate(
        mongoTemplate: ReactiveMongoTemplate,
        environment: Environment,
        converter: MappingMongoConverter?,
        sslProperties: MongoSslProperties,
        sslBundles: ObjectProvider<SslBundles>,
        mongoConnectionPoolProperties: MongoConnectionPoolProperties,
        mongoMetricsConnectionPoolListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
        mongoMetricsCommandListener: ObjectProvider<MongoMetricsCommandListener>,
    ): ReactiveMongoTemplate {
        val driveProps = driveMongoProperties(environment)
        // 没有设置独立数据库时，复用默认的 ReactiveMongoDatabaseFactory
        return if (driveProps == null || driveProps.determineUri() == MongoProperties.DEFAULT_URI) {
            mongoTemplate
        } else {
            val factory = createReactiveMongoDatabaseFactory(
                driveProps,
                sslProperties,
                sslBundles,
                mongoConnectionPoolProperties,
                mongoMetricsConnectionPoolListener,
                mongoMetricsCommandListener
            )
            ReactiveMongoTemplate(factory, converter)
        }
    }

    private fun createReactiveMongoDatabaseFactory(
        properties: MongoProperties,
        sslProperties: MongoSslProperties,
        sslBundles: ObjectProvider<SslBundles>,
        mongoConnectionPoolProperties: MongoConnectionPoolProperties,
        mongoMetricsConnectionPoolListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
        mongoMetricsCommandListener: ObjectProvider<MongoMetricsCommandListener>,
    ): ReactiveMongoDatabaseFactory {
        val connectionString = PropertiesMongoConnectionDetails(properties).connectionString
        val client =
            createReactiveMongoClient(
                properties,
                sslProperties,
                sslBundles,
                mongoConnectionPoolProperties,
                mongoMetricsConnectionPoolListener,
                mongoMetricsCommandListener
            )
        val database = properties.database ?: connectionString.database
        return SimpleReactiveMongoDatabaseFactory(client, database)
    }

    /**
     * 创建MongoClient,需要手动应用customizer
     */
    private fun createReactiveMongoClient(
        properties: MongoProperties,
        sslProperties: MongoSslProperties,
        sslBundles: ObjectProvider<SslBundles>,
        mongoConnectionPoolProperties: MongoConnectionPoolProperties,
        mongoMetricsConnectionPoolListener: ObjectProvider<MongoMetricsConnectionPoolListener>,
        mongoMetricsCommandListener: ObjectProvider<MongoMetricsCommandListener>,
    ): MongoClient {
        val settingsBuilder = MongoClientSettings.builder()
        // metrics
        mongoMetricsConnectionPoolListener.ifAvailable?.let { listener ->
            settingsBuilder.applyToConnectionPoolSettings { it.addConnectionPoolListener(listener) }
        }
        mongoMetricsCommandListener.ifAvailable?.let { listener ->
            settingsBuilder.addCommandListener(listener)
        }

        // standard
        StandardMongoClientSettingsBuilderCustomizer(
            PropertiesMongoConnectionDetails(properties).getConnectionString(),
            properties.uuidRepresentation, properties.ssl, sslBundles.getIfAvailable()
        ).customize(settingsBuilder)

        // custom
        MongoReactiveAutoConfiguration.MongoClientCustomizer(
            sslProperties,
            mongoConnectionPoolProperties
        ).customize(settingsBuilder)

        // netty
        driveMongoEventLoopGroup = NioEventLoopGroup()
        val transportSettings = TransportSettings.nettyBuilder().eventLoopGroup(driveMongoEventLoopGroup!!).build()
        settingsBuilder.transportSettings(transportSettings)

        // create client
        return ReactiveMongoClientFactory(emptyList()).createMongoClient(settingsBuilder.build())
    }

    @PreDestroy
    fun destroy() {
        // destroy mongo netty event loop
        val eventLoopGroup = driveMongoEventLoopGroup
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully().awaitUninterruptibly()
            driveMongoEventLoopGroup = null
        }
    }

    /**
     * 从 Environment 中绑定 drive 专用的 MongoProperties，
     * 不注册为 Bean，避免与 Spring Boot 自动配置的 MongoProperties 产生类型冲突。
     */
    private fun driveMongoProperties(environment: Environment): MongoProperties? {
        return Binder.get(environment)
            .bind("spring.data.mongodb.drive", MongoProperties::class.java)
            .orElseGet { null }
    }
}
