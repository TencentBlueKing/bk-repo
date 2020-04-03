package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.constant.SYSTEM_ERROR_LOGGER_NAME
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.event.StoreFailureEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.io.File

/**
 * 文件存储接口
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractFileStorage<Credentials : StorageCredentials, Client: Any> : FileStorage {

    @Autowired
    protected lateinit var storageProperties: StorageProperties

    @Autowired
    private lateinit var publisher: ApplicationEventPublisher

    private val clientCache: LoadingCache<Credentials, Client> by lazy {
        val cacheLoader = object : CacheLoader<Credentials, Client>() {
            override fun load(credentials: Credentials): Client = onCreateClient(credentials)
        }
        CacheBuilder.newBuilder().maximumSize(storageProperties.maxClientPoolSize).build(cacheLoader)
    }

    var defaultClient: Client? = null

    override fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        store(path, filename, file, client)
    }

    override fun load(path: String, filename: String, received: File, storageCredentials: StorageCredentials): File? {
        val client = getClient(storageCredentials)
        return load(path, filename, received, client)
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
        logger.error("Failed to store file[$filename] on [$storageCredentials].", exception)
        val event = StoreFailureEvent(path, filename, file.absolutePath, storageCredentials)
        publisher.publishEvent(event)
    }

    private fun getClient(storageCredentials: StorageCredentials): Client {
        return if (storageCredentials == getDefaultCredentials()) {
            if (defaultClient == null) {
                defaultClient = onCreateClient(storageCredentials as Credentials)
            }
            defaultClient!!
        } else {
            clientCache.get(storageCredentials as Credentials)
        }
    }

    private fun getCredentialsOrDefault(storageCredentials: StorageCredentials?): StorageCredentials {
        return storageCredentials ?: getDefaultCredentials()
    }

    protected abstract fun onCreateClient(credentials: Credentials): Client
    abstract fun store(path: String, filename: String, file: File, client: Client)
    abstract fun load(path: String, filename: String, received: File, client: Client): File?
    abstract fun delete(path: String, filename: String, client: Client)
    abstract fun exist(path: String, filename: String, client: Client): Boolean

    companion object {
        private val logger = LoggerFactory.getLogger(SYSTEM_ERROR_LOGGER_NAME)
    }
}
