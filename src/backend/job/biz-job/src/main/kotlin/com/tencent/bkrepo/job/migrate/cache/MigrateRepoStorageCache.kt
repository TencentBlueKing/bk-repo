package com.tencent.bkrepo.job.migrate.cache

import com.google.common.cache.CacheBuilder
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MigrateRepoStorageCache {

    private val cache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build<String, Boolean>()

    fun get(projectId: String, repoName: String, loader: () -> Boolean): Boolean {
        return cache.get(cacheKey(projectId, repoName), loader)
    }

    fun invalidate(projectId: String, repoName: String) {
        cache.invalidate(cacheKey(projectId, repoName))
    }

    private fun cacheKey(projectId: String, repoName: String) = "$projectId/$repoName"
}
