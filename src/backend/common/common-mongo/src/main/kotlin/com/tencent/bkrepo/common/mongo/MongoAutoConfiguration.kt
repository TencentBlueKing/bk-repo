/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.mongo

import com.mongodb.Block
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.connection.ConnectionPoolSettings
import com.mongodb.connection.SocketSettings
import com.tencent.bkrepo.common.mongo.dao.util.MongoSslUtils
import com.tencent.bkrepo.common.mongo.properties.MongoSslProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.PropertySource
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.util.concurrent.TimeUnit

/**
 * mongodb 4.0+开始支持事物，但springboot data mongo为了兼容老版本不出错，默认不开启事物
 * 同时，mongodb 事物控制只能用与多副本集的情况，否则会报错，因此添加了enabled开关
 */
@Configuration
@PropertySource("classpath:common-mongo.properties")
@EnableConfigurationProperties(MongoSslProperties::class)
class MongoAutoConfiguration {

    @Bean
    @ConditionalOnProperty(value = ["spring.data.mongodb.transaction.enabled"], matchIfMissing = true)
    fun transactionManager(mongoDatabaseFactory: MongoDatabaseFactory): MongoTransactionManager {
        return MongoTransactionManager(mongoDatabaseFactory)
    }

    /**
     * remove _class
     */
    @Bean
    fun mappingMongoConverter(
        mongoProperties: MongoProperties,
        mongoDatabaseFactory: MongoDatabaseFactory
    ): MappingMongoConverter {
        val dbRefResolver = DefaultDbRefResolver(mongoDatabaseFactory)

        val conversions = MongoCustomConversions(emptyList<Any>())
        val mappingContext = MongoMappingContext()
        mappingContext.setSimpleTypeHolder(conversions.simpleTypeHolder)
        mappingContext.afterPropertiesSet()
        mappingContext.isAutoIndexCreation = mongoProperties.isAutoIndexCreation

        val converter = MappingMongoConverter(dbRefResolver, mappingContext)
        converter.setTypeMapper(DefaultMongoTypeMapper(null))
        converter.afterPropertiesSet()
        converter.setMapKeyDotReplacement("#dot#")
        return converter
    }

    @Bean
    @Primary
    fun mongoProperties(): MongoProperties {
        return MongoProperties()
    }

    @Bean
    @Primary
    fun mongoTemplate(factory: MongoDatabaseFactory, converter: MongoConverter?): MongoTemplate {
        return MongoTemplate(factory, converter)
    }

    @Bean
    fun mongoClientCustomizer(mongoSslProperties: MongoSslProperties): MongoClientSettingsBuilderCustomizer {
        logger.info("Init MongoClientSettingsBuilderCustomizer")
        val socketSettings = Block<SocketSettings.Builder> { builder ->
            builder.connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
        }

        val connectionPoolSettings = Block<ConnectionPoolSettings.Builder> { builder ->
            builder.minSize(200).maxSize(500)
                .maxConnectionLifeTime(0, TimeUnit.SECONDS)
                .maxConnectionIdleTime(0, TimeUnit.SECONDS)
        }

        return MongoClientSettingsBuilderCustomizer { clientSettingsBuilder ->
            clientSettingsBuilder
                .writeConcern(WriteConcern.W1)
                .readConcern(ReadConcern.LOCAL)
                .readPreference(ReadPreference.primaryPreferred())
                .applyToSocketSettings(socketSettings)
                .applyToConnectionPoolSettings(connectionPoolSettings)

            // 根据配置文件判断是否开启ssl
            if (mongoSslProperties.enabled) {
                clientSettingsBuilder.applyToSslSettings { ssl ->
                    ssl.enabled(true)
                    ssl.invalidHostNameAllowed(mongoSslProperties.invalidHostnameAllowed)

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
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MongoAutoConfiguration::class.java)
    }
}
