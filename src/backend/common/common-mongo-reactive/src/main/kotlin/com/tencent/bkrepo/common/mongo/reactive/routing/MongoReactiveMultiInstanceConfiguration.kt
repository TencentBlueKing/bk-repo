package com.tencent.bkrepo.common.mongo.reactive.routing

import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.beans.factory.ObjectProvider
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
    private val syncStateDao: ObjectProvider<MigrationSyncStateDao>,
) {

    @Bean
    fun mongoReactiveRoutingRegistry(): MongoReactiveRoutingRegistry =
        MongoReactiveRoutingRegistry(properties, syncStateDao.ifAvailable)
}
