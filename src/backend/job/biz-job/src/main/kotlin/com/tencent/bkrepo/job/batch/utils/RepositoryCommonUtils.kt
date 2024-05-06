package com.tencent.bkrepo.job.batch.utils

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RepositoryCommonUtils(
    storageCredentialsClient: StorageCredentialsClient,
    repositoryClient: RepositoryClient
) {

    init {
        Companion.storageCredentialsClient = storageCredentialsClient
        Companion.repositoryClient = repositoryClient
    }

    companion object {
        private lateinit var storageCredentialsClient: StorageCredentialsClient
        private lateinit var repositoryClient: RepositoryClient
        private val repositoryCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build<RepositoryId, RepositoryDetail>()

        private val storageCredentialsCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<String, StorageCredentials?>()

        fun getStorageCredentials(credentialsKey: String): StorageCredentials? {
            return storageCredentialsCache.getIfPresent(credentialsKey) ?: storageCredentialsClient.findByKey(
                credentialsKey
            ).data?.apply { storageCredentialsCache.put(credentialsKey, this) }
        }

        fun getRepositoryDetail(
            projectId: String,
            repoName: String,
            type: RepositoryType = RepositoryType.NONE
        ): RepositoryDetail {
            val repositoryId = RepositoryId(projectId, repoName, type)
            return repositoryCache.getOrPut(repositoryId) {
                repositoryClient.getRepoDetail(projectId, repoName, type.name).data
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
