package com.tencent.bkrepo.common.quartz

import com.novemberain.quartz.mongodb.MongoDBJobStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.autoconfigure.quartz.QuartzProperties
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.scheduling.quartz.SchedulerFactoryBean

@Configuration
@ConditionalOnClass(MongoDBJobStore::class)
@EnableConfigurationProperties(
    QuartzProperties::class,
    MongoProperties::class
)
@ConditionalOnProperty(prefix = "spring.quartz", name = ["mongo"], matchIfMissing = true, havingValue = "true")
@PropertySource("classpath:common-quartz.yml")
class QuartzMongoConfiguration {

    @Bean
    fun quartzMongoCustomizer(quartzProperties: QuartzProperties, mongoProperties: MongoProperties): SchedulerFactoryBeanCustomizer {
        return QuartzMongoCustomizer(quartzProperties, mongoProperties)
    }

    private class QuartzMongoCustomizer(private val quartzProperties: QuartzProperties, private val mongoProperties: MongoProperties) : SchedulerFactoryBeanCustomizer {

        override fun customize(schedulerFactoryBean: SchedulerFactoryBean?) {
            val properties = quartzProperties.properties
            properties["org.quartz.jobStore.class"] = MongoDBJobStore::class.qualifiedName
            properties["org.quartz.threadPool.threadCount"] = 1.toString()
            properties["org.quartz.jobStore.mongoUri"] = mongoProperties.uri
            properties["org.quartz.jobStore.dbName"] = mongoProperties.mongoClientDatabase
        }
    }
}
