package com.tencent.bkrepo.common.quartz

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.autoconfigure.quartz.QuartzProperties
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource

@Configuration
@EnableConfigurationProperties(
    QuartzProperties::class,
    MongoProperties::class
)
@ConditionalOnProperty(prefix = "spring.quartz", name = ["mongo"], matchIfMissing = true, havingValue = "true")
@PropertySource("classpath:common-quartz.properties")
class QuartzMongoConfiguration {

    @Bean
    fun mongoDatabase(mongoProperties: MongoProperties, mongoClient: MongoClient): MongoDatabase {
        val databaseName = mongoProperties.mongoClientDatabase
        return mongoClient.getDatabase(databaseName)
    }

    @Bean
    fun quartzMongoCustomizer(
        quartzProperties: QuartzProperties,
        mongoProperties: MongoProperties,
        mongoDatabase: MongoDatabase
    ): SchedulerFactoryBeanCustomizer {
        Companion.mongoDatabase = mongoDatabase
        return SchedulerFactoryBeanCustomizer {
            val properties = quartzProperties.properties
            properties["org.quartz.jobStore.class"] = MongoJobStore::class.qualifiedName
            // properties["org.quartz.threadPool.threadCount"] = 20.toString()
        }
    }

    companion object {
        lateinit var mongoDatabase: MongoDatabase
    }
}
