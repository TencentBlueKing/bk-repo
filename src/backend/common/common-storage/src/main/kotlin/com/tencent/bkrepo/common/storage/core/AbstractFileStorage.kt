package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
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

    private val loadingCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .removalListener<Credentials, Client> { onClientRemoval(it.key, it.value) }
            .build(object : CacheLoader<Credentials, Client>() {
                override fun load(credentials: Credentials): Client = createClient(credentials)
            })

    override fun store(hash: String, inputStream: InputStream, credentials: Credentials?) {
        // 转存本地文件
        // 1. 并行上传 加速，
        // 2. 支持重试
        // 3. 下载缓存
        val path = locateStrategy.locate(hash)
        store(path, hash, inputStream, loadingCache.get(credentials ?: defaultCredentials))
    }

    override fun delete(hash: String, credentials: Credentials?) {
        val path = locateStrategy.locate(hash)
        delete(path, hash, loadingCache.get(credentials ?: defaultCredentials))
    }

    override fun load(hash: String, credentials: Credentials?): InputStream? {
        // 查看本是否有文件
        val path = locateStrategy.locate(hash)
        return load(path, hash, loadingCache.get(credentials ?: defaultCredentials))
    }

    override fun exist(hash: String, credentials: Credentials?): Boolean {
        val path = locateStrategy.locate(hash)
        return exist(path, hash, loadingCache.get(credentials ?: defaultCredentials))
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
    protected abstract fun delete(path: String, filename: String, client: Client)
    protected abstract fun load(path: String, filename: String, client: Client): InputStream?
    protected abstract fun exist(path: String, filename: String, client: Client): Boolean

    companion object {
        /**
         * client 最大缓存数量
         */
        private const val MAX_CACHE_SIZE = 20L
    }
}
