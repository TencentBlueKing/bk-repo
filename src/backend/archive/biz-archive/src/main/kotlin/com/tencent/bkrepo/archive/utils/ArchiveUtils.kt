package com.tencent.bkrepo.archive.utils

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue

@Component
class ArchiveUtils(
    storageCredentialsClient: StorageCredentialsClient,
    storageProperties: StorageProperties,
    repositoryClient: RepositoryClient,
    archiveProperties: ArchiveProperties,
) {

    init {
        Companion.storageCredentialsClient = storageCredentialsClient
        Companion.repositoryClient = repositoryClient
        Companion.archiveProperties = archiveProperties
        defaultStorageCredentials = storageProperties.defaultStorageCredentials()
    }

    companion object {
        private lateinit var storageCredentialsClient: StorageCredentialsClient
        private lateinit var defaultStorageCredentials: StorageCredentials
        private lateinit var repositoryClient: RepositoryClient
        private lateinit var archiveProperties: ArchiveProperties
        private val logger = LoggerFactory.getLogger(ArchiveUtils::class.java)
        private val storageCredentialsCache: LoadingCache<String, StorageCredentials> = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(CacheLoader.from { key -> loadStorageCredentials(key) })

        private fun loadStorageCredentials(key: String): StorageCredentials {
            if (key.isEmpty()) return defaultStorageCredentials
            return storageCredentialsClient.findByKey(key).data ?: defaultStorageCredentials
        }

        fun getStorageCredentials(key: String?): StorageCredentials {
            return storageCredentialsCache.get(key.orEmpty()).apply {
                // 指定使用归档工作路径进行cos分片下载
                upload.location = archiveProperties.workDir
            }
        }

        fun newFixedAndCachedThreadPool(
            threads: Int,
            threadFactory: ThreadFactory,
            queue: BlockingQueue<Runnable> = ArrayBlockingQueue(DEFAULT_BUFFER_SIZE),
        ): ThreadPoolExecutor {
            return ThreadPoolExecutor(
                threads,
                threads,
                60,
                TimeUnit.SECONDS,
                queue,
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
    }
}
