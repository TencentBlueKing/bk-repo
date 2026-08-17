package com.tencent.bkrepo.preview.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter

/**
 * 素材分享等 preview 业务集合可单独配置 Mongo：
 * `spring.data.mongodb.preview.uri`。未配置时回退默认 mongoTemplate。
 */
@Configuration(proxyBeanMethods = false)
class PreviewMongoConfiguration {

    @Bean
    @ConfigurationProperties("spring.data.mongodb.preview")
    fun previewMongoProperties(): MongoProperties = MongoProperties()

    @Bean
    @DependsOn("mongoTemplate")
    fun previewMongoTemplate(
        mongoTemplate: MongoTemplate,
        @Qualifier("previewMongoProperties") previewMongoProperties: MongoProperties,
        converter: MappingMongoConverter?,
    ): MongoTemplate {
        val uri = previewMongoProperties.uri
        if (uri.isNullOrBlank()) {
            return mongoTemplate
        }
        val factory = SimpleMongoClientDatabaseFactory(
            MongoClients.create(
                MongoClientSettings.builder()
                    .applyConnectionString(ConnectionString(uri))
                    .build(),
            ),
            ConnectionString(uri).database ?: previewMongoProperties.mongoClientDatabase,
        )
        return if (converter != null) {
            MongoTemplate(factory, converter)
        } else {
            MongoTemplate(factory)
        }
    }
}
