package com.tencent.bkrepo.archive.utils

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.archive.job.JobProcessMonitor
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ArchiveUtils(
    storageCredentialsClient: StorageCredentialsClient,
    storageProperties: StorageProperties,
    repositoryClient: RepositoryClient,
) {

    init {
        Companion.storageCredentialsClient = storageCredentialsClient
        Companion.repositoryClient = repositoryClient
        defaultStorageCredentials = storageProperties.defaultStorageCredentials()
    }

    companion object {
        private lateinit var storageCredentialsClient: StorageCredentialsClient
        private lateinit var defaultStorageCredentials: StorageCredentials
        private lateinit var repositoryClient: RepositoryClient
        private val logger = LoggerFactory.getLogger(ArchiveUtils::class.java)
        private val storageCredentialsCache: LoadingCache<String, StorageCredentials> = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(CacheLoader.from { key -> loadStorageCredentials(key) })
        private val repositoryDetailCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<RepositoryId, RepositoryDetail>()

        val monitor = JobProcessMonitor()

        init {
            monitor.start()
        }

        private fun loadStorageCredentials(key: String): StorageCredentials {
            if (key.isEmpty()) return defaultStorageCredentials
            return storageCredentialsClient.findByKey(key).data ?: defaultStorageCredentials
        }

        fun getStorageCredentials(key: String?): StorageCredentials {
            return storageCredentialsCache.get(key.orEmpty())
        }

        fun getRepositoryDetail(project: String, repoName: String): RepositoryDetail {
            val repoId = RepositoryId(project, repoName)
            return repositoryDetailCache.get(repoId) {
                repositoryClient.getRepoDetail(project, repoName).data
                    ?: throw RepoNotFoundException("$project/$repoName")
            }
        }

        fun supportXZCmd(): Boolean {
            return try {
                Runtime.getRuntime().exec("xz -V")
                true
            } catch (ignore: Exception) {
                false
            }
        }

        fun newFixedAndCachedThreadPool(threads: Int, threadFactory: ThreadFactory): ThreadPoolExecutor {
            return ThreadPoolExecutor(
                threads,
                threads,
                60,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(DEFAULT_BUFFER_SIZE),
                threadFactory,
                ThreadPoolExecutor.CallerRunsPolicy(),
            )
        }

        fun runCmd(cmd: List<String>) {
            logger.debug("# ${cmd.joinToString(" ")}")
            val pb = ProcessBuilder()
            pb.command(cmd)
            val process = pb.start()
            val ev = process.waitFor()
            if (ev != 0) {
                error("cmd failed,exit: $ev.")
            }
        }

        data class RepositoryId(
            val project: String,
            val repoName: String,
        )
    }
}
