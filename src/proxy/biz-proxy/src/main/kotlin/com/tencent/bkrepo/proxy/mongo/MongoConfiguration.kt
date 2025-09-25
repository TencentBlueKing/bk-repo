package com.tencent.bkrepo.proxy.mongo

import com.tencent.bkrepo.common.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate


@AutoConfiguration(
    after = [MongoAutoConfiguration::class]
)
class MongoConfiguration {

    @Bean
    fun mongoTemplate(mongoDbFactory: MongoDatabaseFactory): MongoTemplate {
        return CustomMongoTemplate(mongoDbFactory)
    }
}