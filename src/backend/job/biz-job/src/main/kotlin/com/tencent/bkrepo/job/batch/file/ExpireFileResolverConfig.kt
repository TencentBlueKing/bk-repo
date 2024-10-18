package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileRetainResolver
import com.tencent.bkrepo.job.config.properties.ExpiredCacheFileCleanupJobProperties
import com.tencent.bkrepo.job.service.FileCacheService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
class ExpireFileResolverConfig {
    @Bean
    fun fileRetainResolver(
        expiredCacheFileCleanupJobProperties: ExpiredCacheFileCleanupJobProperties,
        fileCacheService: FileCacheService,
        mongoTemplate: MongoTemplate
    ): FileRetainResolver {
        return BasedRepositoryNodeRetainResolver(
            expiredCacheFileCleanupJobProperties.repoConfig,
            fileCacheService,
            mongoTemplate
        )
    }
}
