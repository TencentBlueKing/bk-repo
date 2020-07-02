package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.event.StoreFailureEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.io.InputStream
import kotlin.system.measureNanoTime

/**
 * 文件存储接口
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
@Suppress("UNCHECKED_CAST")
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
        onCreateClient(getDefaultCredentials() as Credentials)
    }

    override fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        val size = file.length()
        val nanoTime = measureNanoTime {
            store(path, filename, file, client)
        }
        logger.info("Success to persist file [$filename], size: ${HumanReadable.size(size)}, elapse: ${HumanReadable.time(nanoTime)}, " +
            "average: ${HumanReadable.throughput(size, nanoTime)}.")
    }

    override fun store(path: String, filename: String, inputStream: InputStream, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        val size = inputStream.available().toLong()
        val nanoTime = measureNanoTime {
            store(path, filename, inputStream, client)
        }
        logger.info("Success to persist stream [$filename], size: ${HumanReadable.size(size)}, elapse: ${HumanReadable.time(nanoTime)}, " +
            "average: ${HumanReadable.throughput(size, nanoTime)}.")
    }

    override fun load(path: String, filename: String, received: File, storageCredentials: StorageCredentials): File? {
        return try {
            val client = getClient(storageCredentials)
            return load(path, filename, received, client)
        } catch (ex: Exception) {
            logger.warn("Failed to load file[$filename]: ${ex.message}", ex)
            null
        }
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
        return if (storageCredentials == getDefaultCredentials()) {
            defaultClient
        } else {
            clientCache.get(storageCredentials as Credentials)
        }
    }

    private fun getCredentialsOrDefault(storageCredentials: StorageCredentials?): StorageCredentials {
        return storageCredentials ?: getDefaultCredentials()
    }

    protected abstract fun onCreateClient(credentials: Credentials): Client
    abstract fun store(path: String, filename: String, file: File, client: Client)
    abstract fun store(path: String, filename: String, inputStream: InputStream, client: Client)
    abstract fun load(path: String, filename: String, received: File, client: Client): File?
    abstract fun load(path: String, filename: String, range: Range, client: Client): InputStream?
    abstract fun delete(path: String, filename: String, client: Client)
    abstract fun exist(path: String, filename: String, client: Client): Boolean

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileStorage::class.java)
        private const val MAX_CACHE_CLIENT = 10L
    }
}
