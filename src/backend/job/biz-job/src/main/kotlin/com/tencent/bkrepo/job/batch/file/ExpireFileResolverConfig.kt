package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileRetainResolver
import com.tencent.bkrepo.job.config.properties.ExpiredCacheFileCleanupJobProperties
import com.tencent.bkrepo.job.service.FileCacheService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ExpireFileResolverConfig {
    @Bean
    fun fileRetainResolver(
        expiredCacheFileCleanupJobProperties: ExpiredCacheFileCleanupJobProperties,
        scheduler: ThreadPoolTaskScheduler,
        fileCacheService: FileCacheService,
        mongoTemplate: MongoTemplate,
        redisTemplate: RedisTemplate<String, String>,
        lockOperation: LockOperation
    ): FileRetainResolver {
        return BasedRepositoryNodeRetainResolver(
            expiredCacheFileCleanupJobProperties.repoConfig,
            scheduler,
            fileCacheService,
            mongoTemplate,
            redisTemplate,
            lockOperation
        )
    }
}
