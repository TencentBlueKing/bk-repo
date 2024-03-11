package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileExpireResolver
import com.tencent.bkrepo.job.config.properties.ExpiredCacheFileCleanupJobProperties
import com.tencent.bkrepo.job.service.FileCacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ExpireFileResolverConfig {

    @Lazy
    @Autowired
    private lateinit var nodeSearchService: NodeSearchService

    @Bean
    fun fileExpireResolver(
        expiredCacheFileCleanupJobProperties: ExpiredCacheFileCleanupJobProperties,
        scheduler: ThreadPoolTaskScheduler,
        fileCacheService: FileCacheService
    ): FileExpireResolver {
        return BasedRepositoryFileExpireResolver(
            nodeSearchService,
            expiredCacheFileCleanupJobProperties.repoConfig,
            scheduler,
            fileCacheService
        )
    }
}
