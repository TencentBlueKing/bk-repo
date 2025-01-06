package com.tencent.bkrepo.job.batch.utils

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RepositoryCommonUtils(
    storageCredentialService: StorageCredentialService,
    repositoryService: RepositoryService
) {

    init {
        Companion.storageCredentialService = storageCredentialService
        Companion.repositoryService = repositoryService
    }

    companion object {
        private lateinit var storageCredentialService: StorageCredentialService
        private lateinit var repositoryService: RepositoryService
        private val repositoryCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build<RepositoryId, RepositoryDetail>()

        private val storageCredentialsCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<String, StorageCredentials?>()

        fun getStorageCredentials(credentialsKey: String): StorageCredentials? {
            return storageCredentialsCache.getIfPresent(credentialsKey) ?: storageCredentialService.findByKey(
                credentialsKey
            )?.apply { storageCredentialsCache.put(credentialsKey, this) }
        }

        fun getRepositoryDetail(
            projectId: String,
            repoName: String,
            type: RepositoryType = RepositoryType.NONE
        ): RepositoryDetail {
            val repositoryId = RepositoryId(projectId, repoName, type)
            return repositoryCache.getOrPut(repositoryId) {
                repositoryService.getRepoDetail(projectId, repoName, type.name)
                    ?: throw RepoNotFoundException("$projectId/$repoName")
            }
        }
    }

    data class RepositoryId(val projectId: String, val repoName: String, val type: RepositoryType) {
        override fun toString(): String {
            return "$projectId/$repoName#$type"
        }
    }
}

fun <K : Any, V : Any> Cache<K, V>.getOrPut(key: K, defaultValue: () -> V): V {
    return this.getIfPresent(key) ?: let {
        defaultValue().apply { it.put(key, this) }
    }
}
