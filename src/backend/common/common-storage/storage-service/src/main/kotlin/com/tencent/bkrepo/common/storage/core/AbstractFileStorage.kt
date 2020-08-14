package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.event.StoreFailureEvent
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.io.InputStream
import kotlin.system.measureNanoTime

/**
 * 文件存储接口
 */
@Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
abstract class AbstractFileStorage<Credentials : StorageCredentials, Client> : FileStorage {

    @Autowired
    protected lateinit var storageProperties: StorageProperties

    @Autowired
    private lateinit var publisher: ApplicationEventPublisher

    private val clientCache: LoadingCache<Credentials, Client> by lazy {
        val cacheLoader = object : CacheLoader<Credentials, Client>() {
            override fun load(credentials: Credentials): Client = onCreateClient(credentials)
        }
        CacheBuilder.newBuilder().maximumSize(MAX_CACHE_CLIENT).build(cacheLoader)
    }

    val defaultClient: Client by lazy {
        onCreateClient(storageProperties.defaultStorageCredentials() as Credentials)
    }

    override fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        val size = file.length()
        val nanoTime = measureNanoTime {
            store(path, filename, file, client)
        }
        val throughput = Throughput(size, nanoTime)
        logger.info("Success to persist file [$filename], $throughput.")
    }

    override fun store(path: String, filename: String, inputStream: InputStream, size: Long, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        val nanoTime = measureNanoTime {
            store(path, filename, inputStream, size, client)
        }
        val throughput = Throughput(size, nanoTime)
        logger.info("Success to persist stream [$filename], $throughput.")
    }

    override fun load(path: String, filename: String, range: Range, storageCredentials: StorageCredentials): InputStream? {
        return try {
            val client = getClient(storageCredentials)
            load(path, filename, range, client)
        } catch (ex: Exception) {
            logger.warn("Failed to load stream[$filename]: ${ex.message}", ex)
            null
        }
    }

    override fun delete(path: String, filename: String, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        delete(path, filename, client)
    }

    override fun exist(path: String, filename: String, storageCredentials: StorageCredentials): Boolean {
        val client = getClient(storageCredentials)
        return exist(path, filename, client)
    }

    override fun recover(exception: Exception, path: String, filename: String, file: File, storageCredentials: StorageCredentials) {
        val event = StoreFailureEvent(path, filename, file.absolutePath, storageCredentials, exception)
        publisher.publishEvent(event)
    }

    private fun getClient(storageCredentials: StorageCredentials): Client {
        return if (storageCredentials == storageProperties.defaultStorageCredentials()) {
            defaultClient
        } else {
            clientCache.get(storageCredentials as Credentials)
        }
    }

    protected abstract fun onCreateClient(credentials: Credentials): Client
    abstract fun store(path: String, filename: String, file: File, client: Client)
    abstract fun store(path: String, filename: String, inputStream: InputStream, size: Long, client: Client)
    abstract fun load(path: String, filename: String, range: Range, client: Client): InputStream?
    abstract fun delete(path: String, filename: String, client: Client)
    abstract fun exist(path: String, filename: String, client: Client): Boolean

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileStorage::class.java)
        private const val MAX_CACHE_CLIENT = 10L
    }
}
