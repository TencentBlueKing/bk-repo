package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.dao.RoutingConfigDao
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MongoMigrationDaoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun migrationSyncStateDao(): MigrationSyncStateDao = MigrationSyncStateDao()

    @Bean
    @ConditionalOnMissingBean
    fun routingConfigDao(): RoutingConfigDao = RoutingConfigDao()
}
