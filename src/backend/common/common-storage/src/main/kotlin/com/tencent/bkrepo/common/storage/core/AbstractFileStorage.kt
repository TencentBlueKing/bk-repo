package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream

/**
 * 文件存储抽象类
 * 对于上层来说，只需要提供文件hash值（如sha256），不需要关心文件如何落地与定位，统一由本层的LocateStrategy去判断，以达到更均衡的文件散列分布，同时避免文件冲突。
 * 通常来说上层会计算文件的hash(完整性校验等)，考虑到性能问题，因此hash统一由上层计算好传递进来。
 * 封装了身份信息和存储客户端的缓存机制
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
abstract class AbstractFileStorage<Credentials: ClientCredentials, Client>(
    private val locateStrategy: LocateStrategy,
    private val properties: StorageProperties
) : FileStorage {

    private var clientCache: LoadingCache<Credentials, Client>? = null

    private var localFileCache: LocalFileCache? = null

    init {
        if(properties.clientCache.enabled) {
            clientCache = CacheBuilder.newBuilder()
                    .maximumSize(properties.clientCache.size)
                    .removalListener<Credentials, Client> { onClientRemoval(it.key, it.value) }
                    .build(object : CacheLoader<Credentials, Client>() {
                        override fun load(credentials: Credentials): Client = createClient(credentials)
                    })
        }

        if(properties.localCache.enabled) {
            localFileCache = LocalFileCache(properties.localCache.path)
        }

    }

    private fun getClient(clientCredentials: ClientCredentials?): Client {
        val credentials = (clientCredentials ?: properties.credentials) as Credentials
        return clientCache?.get(credentials) ?: createClient(credentials)
    }

    override fun store(hash: String, inputStream: InputStream, credentials: ClientCredentials?) {
        val path = locateStrategy.locate(hash)

        localFileCache?.run {
            val cachedFile = this.cache(path, hash, inputStream)
            store(path, hash, cachedFile, getClient(credentials))
        } ?: store(path, hash, inputStream, getClient(credentials))
    }

    override fun delete(hash: String, credentials: ClientCredentials?) {
        val path = locateStrategy.locate(hash)

        delete(path, hash, getClient(credentials))
        localFileCache?.remove(path, hash)

    }

    override fun load(hash: String, credentials: ClientCredentials?): InputStream? {
        val path = locateStrategy.locate(hash)

        return localFileCache?.run {
            this.get(path, hash)?.inputStream() ?: let {
                val inputStream = load(path, hash, getClient(credentials))
                inputStream?.let { this.cache(path, hash, it) }
                inputStream
            }
        } ?: load(path, hash, getClient(credentials))

    }

    override fun exist(hash: String, credentials: ClientCredentials?): Boolean {
        val path = locateStrategy.locate(hash)

        return localFileCache?.run {
            this.get(path, hash)?.let{ true }
        } ?: exist(path, hash, getClient(credentials))
    }

    /**
     * 根据credentials创建client
     */
    abstract fun createClient(credentials: Credentials): Client

    /**
     * 缓存清理时回调处理
     */
    protected open fun onClientRemoval(credentials: Credentials, client: Client) {
        // do nothing
    }


    protected abstract fun store(path: String, filename: String, inputStream: InputStream, client: Client)
    protected abstract fun store(path: String, filename: String, file: File, client: Client)
    protected abstract fun delete(path: String, filename: String, client: Client)
    protected abstract fun load(path: String, filename: String, client: Client): InputStream?
    protected abstract fun exist(path: String, filename: String, client: Client): Boolean

}
