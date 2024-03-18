package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileExpireResolver
import com.tencent.bkrepo.job.config.properties.ExpiredCacheFileCleanupJobProperties
import com.tencent.bkrepo.job.service.FileCacheService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ExpireFileResolverConfig {


    @Bean
    fun fileExpireResolver(
        expiredCacheFileCleanupJobProperties: ExpiredCacheFileCleanupJobProperties,
        scheduler: ThreadPoolTaskScheduler,
        fileCacheService: FileCacheService,
        mongoTemplate: MongoTemplate
    ): FileExpireResolver {
        return BasedRepositoryFileExpireResolver(
            expiredCacheFileCleanupJobProperties.repoConfig,
            scheduler,
            fileCacheService,
            mongoTemplate
        )
    }
}
