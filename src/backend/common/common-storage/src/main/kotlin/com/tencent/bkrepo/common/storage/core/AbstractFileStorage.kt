package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
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
abstract class AbstractFileStorage<Credentials, Client>(
    private val locateStrategy: LocateStrategy,
    val defaultCredentials: Credentials
) : FileStorage<Credentials, Client> {

    private val loadingCache: LoadingCache<Credentials, Client> = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build(object : CacheLoader<Credentials, Client>() {
                override fun load(credentials: Credentials): Client = createClient(credentials)
            })


    override fun store(hash: String, inputStream: InputStream, credentials: Credentials?) {
        val path = locateStrategy.locate(hash)
        store(path, hash, inputStream, loadingCache.get(credentials?:defaultCredentials))
    }

    override fun delete(hash: String, credentials: Credentials?) {
        val path = locateStrategy.locate(hash)
        delete(path, hash, loadingCache.get(credentials?:defaultCredentials))
    }

    override fun load(hash: String, credentials: Credentials?): InputStream? {
        val path = locateStrategy.locate(hash)
        return load(path, hash, loadingCache.get(credentials?:defaultCredentials))
    }

    override fun exist(hash: String, credentials: Credentials?): Boolean {
        val path = locateStrategy.locate(hash)
        return exist(path, hash, loadingCache.get(credentials?:defaultCredentials))
    }

    abstract fun createClient(credentials: Credentials): Client

    protected abstract fun store(path: String, filename: String, inputStream: InputStream, client: Client)
    protected abstract fun delete(path: String, filename: String, client: Client)
    protected abstract fun load(path: String, filename: String, client: Client): InputStream?
    protected abstract fun exist(path: String, filename: String, client: Client): Boolean

    companion object {
        private const val MAX_CACHE_SIZE = 20L
    }

}
