package com.tencent.bkrepo.executor.config

import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

@Configuration
class MultipleMongoConfig {

    @Primary
    @Bean(name = ["primary"])
    @ConfigurationProperties(prefix = "spring.data.mongodb")
    fun getPrimary(): MongoProperties {
        return MongoProperties()
    }

    @Bean
    @Primary
    fun primaryFactory(mongo: MongoProperties): MongoDatabaseFactory {
        return SimpleMongoClientDatabaseFactory(mongo.uri)
    }

    @Bean(name = ["secondary"])
    @ConfigurationProperties(prefix = "executor")
    fun getSecondary(): MongoProperties {
        return MongoProperties()
    }

    @Bean(name = ["secondaryMongoTemplate"])
    fun secondaryMongoTemplate(): MongoTemplate {
        return MongoTemplate(secondaryFactory(getSecondary()))
    }

    @Bean
    fun secondaryFactory(mongo: MongoProperties): MongoDatabaseFactory {
        return SimpleMongoClientDatabaseFactory(mongo.uri)
    }
}
