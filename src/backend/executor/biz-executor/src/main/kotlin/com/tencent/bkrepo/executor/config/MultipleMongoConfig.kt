package com.tencent.bkrepo.executor.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory


@Configuration
class MultipleMongoConfig {

    @Bean(name = ["transaction"])
    @ConditionalOnProperty(value = ["spring.data.mongodb.transaction.enabled"], matchIfMissing = true)
    fun transactionManager(mongoDatabaseFactory: MongoDatabaseFactory): MongoTransactionManager {
        return MongoTransactionManager(mongoDatabaseFactory)
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
