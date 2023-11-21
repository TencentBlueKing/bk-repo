package com.tencent.bkrepo.archive.utils

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.archive.job.JobProcessMonitor
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ArchiveUtils(
    storageCredentialsClient: StorageCredentialsClient,
    storageProperties: StorageProperties,
) {

    init {
        Companion.storageCredentialsClient = storageCredentialsClient
        defaultStorageCredentials = storageProperties.defaultStorageCredentials()
    }

    companion object {
        private lateinit var storageCredentialsClient: StorageCredentialsClient
        private lateinit var defaultStorageCredentials: StorageCredentials
        private val logger = LoggerFactory.getLogger(ArchiveUtils::class.java)
        private val storageCredentialsCache: LoadingCache<String, StorageCredentials> = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(CacheLoader.from { key -> loadStorageCredentials(key) })

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

        fun supportXZCmd(): Boolean {
            return try {
                Runtime.getRuntime().exec("xz -V")
                true
            } catch (ignore: Exception) {
                false
            }
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
