package com.tencent.bkrepo.common.mongo.reactive.routing

import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MongoMultiInstanceProperties::class)
@ConditionalOnProperty(
    prefix = "spring.data.mongodb.multi-instance",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class MongoReactiveMultiInstanceConfiguration(
    private val properties: MongoMultiInstanceProperties,
) {

    @Bean
    fun mongoReactiveRoutingRegistry(): MongoReactiveRoutingRegistry =
        MongoReactiveRoutingRegistry(properties)
}
