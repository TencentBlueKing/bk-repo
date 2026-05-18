package com.tencent.bkrepo.common.mongo

import com.mongodb.MongoClientSettings
import com.tencent.bkrepo.common.mongo.api.properties.MongoConnectionPoolProperties
import com.tencent.bkrepo.common.mongo.api.properties.MongoSslProperties
import com.tencent.bkrepo.common.mongo.api.util.MongoSslUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.PropertySource
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.util.concurrent.TimeUnit

@Configuration
@PropertySource("classpath:common-mongo.properties")
@EnableConfigurationProperties(MongoSslProperties::class, MongoConnectionPoolProperties::class)
class MongoReactiveAutoConfiguration {

    @Bean
    @ConditionalOnProperty(value = ["spring.data.mongodb.transaction.enabled"], matchIfMissing = true)
    fun reactiveTransactionManager(
        reactiveMongoDatabaseFactory: ReactiveMongoDatabaseFactory
    ): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(reactiveMongoDatabaseFactory)
    }

    /**
     * 去除 _class 字段，注册自定义转换器，设置 Map key 中 '.' 的替换符
     *
     * 与同步版 MongoAutoConfiguration.mappingMongoConverter 保持一致
     */
    @Bean
    @ConditionalOnMissingBean
    fun reactiveMappingMongoConverter(
        mongoProperties: MongoProperties,
    ): MappingMongoConverter {
        // Reactive 场景下无法使用 DefaultDbRefResolver（它依赖同步的 MongoDatabaseFactory，会执行阻塞 I/O），
        // 而 MappingMongoConverter 构造函数要求传入 DbRefResolver 实例，这里使用 NoOpDbRefResolver.INSTANCE 作为占位
        val dbRefResolver = NoOpDbRefResolver.INSTANCE

        val conversions = MongoCustomConversions(emptyList<Any>())
        val mappingContext = MongoMappingContext()
        mappingContext.setSimpleTypeHolder(conversions.simpleTypeHolder)
        mappingContext.afterPropertiesSet()
        mappingContext.isAutoIndexCreation = mongoProperties.isAutoIndexCreation

        val converter = MappingMongoConverter(dbRefResolver, mappingContext)
        converter.customConversions = conversions
        converter.setTypeMapper(DefaultMongoTypeMapper(null))
        converter.afterPropertiesSet()
        converter.setMapKeyDotReplacement("#dot#")
        return converter
    }


    @Bean
    @Primary
    fun reactiveMongoTemplate(
        databaseFactory: ReactiveMongoDatabaseFactory,
        converter: MappingMongoConverter?,
    ): ReactiveMongoTemplate {
        return ReactiveMongoTemplate(databaseFactory, converter)
    }

    @Bean
    @ConditionalOnMissingBean
    fun mongoClientCustomizer(
        mongoSslProperties: MongoSslProperties,
        mongoConnectionPoolProperties: MongoConnectionPoolProperties
    ): MongoClientSettingsBuilderCustomizer {
        logger.info("Init MongoReactiveSSLConfiguration")
        return MongoClientCustomizer(mongoSslProperties, mongoConnectionPoolProperties)
    }

    class MongoClientCustomizer(
        private val mongoSslProperties: MongoSslProperties,
        private val mongoConnectionPoolProperties: MongoConnectionPoolProperties
    ) : MongoClientSettingsBuilderCustomizer {
        override fun customize(clientSettingsBuilder: MongoClientSettings.Builder) {
            // 根据配置文件判断是否开启ssl
            if (mongoSslProperties.enabled) {
                clientSettingsBuilder.applyToSslSettings { ssl ->
                    ssl.enabled(true)
                    ssl.invalidHostNameAllowed(!mongoSslProperties.verifyHostname)

                    try {
                        // 根据配置判断使用单向还是双向TLS
                        if (mongoSslProperties.isMutualTlsConfigured()) {
                            logger.info("Detected client certificate configuration - enabling mutual TLS")
                            ssl.context(MongoSslUtils.createMutualTlsSslContext(mongoSslProperties))
                        } else {
                            logger.info("Using one-way TLS configuration")
                            ssl.context(MongoSslUtils.createOnewayTlsSslContext(mongoSslProperties))
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to configure MongoDB TLS context", e)
                        throw RuntimeException("Failed to configure MongoDB TLS context", e)
                    }
                }
            }
            clientSettingsBuilder.applyToConnectionPoolSettings {
                if (mongoConnectionPoolProperties.maxConnectionIdleTimeMS != 0L) {
                    it.maxConnectionIdleTime(
                        mongoConnectionPoolProperties.maxConnectionIdleTimeMS,
                        TimeUnit.MILLISECONDS
                    )
                }
                if (mongoConnectionPoolProperties.maxConnectionLifeTimeMS != 0L) {
                    it.maxConnectionLifeTime(
                        mongoConnectionPoolProperties.maxConnectionLifeTimeMS,
                        TimeUnit.MILLISECONDS
                    )
                }
            }
        }

    }

    companion object {
        private val logger = LoggerFactory.getLogger(MongoReactiveAutoConfiguration::class.java)
    }
}
