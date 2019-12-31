package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.springframework.beans.factory.annotation.Autowired

/**
 * 文件存储接口
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
abstract class AbstractFileStorage<Credentials : StorageCredentials, Client> : FileStorage {

    @Autowired
    protected lateinit var storageProperties: StorageProperties

    private val clientCache: LoadingCache<Credentials, Client> by lazy {
        val cacheLoader = object : CacheLoader<Credentials, Client>() {
            override fun load(credentials: Credentials): Client = onCreateClient(credentials)
        }
        CacheBuilder.newBuilder().maximumSize(storageProperties.maxClientPoolSize).build(cacheLoader)
    }

    @Suppress("UNCHECKED_CAST")
    fun getClient(storageCredentials: StorageCredentials): Client {
        return clientCache.get(storageCredentials as Credentials)
    }

    private fun getCredentialsOrDefault(storageCredentials: StorageCredentials?): StorageCredentials {
        return storageCredentials ?: getDefaultCredentials()
    }

    protected abstract fun onCreateClient(credentials: Credentials): Client
}
