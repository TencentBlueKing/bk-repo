package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.storage.exception.StorageException
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream
import java.lang.Exception
import org.slf4j.LoggerFactory

/**
 * 文件存储抽象类
 * 对于上层来说，只需要提供文件hash值（如sha256），不需要关心文件如何落地与定位，统一由本层的LocateStrategy去判断，以达到更均衡的文件散列分布，同时避免文件冲突。
 * 通常来说上层会计算文件的hash(完整性校验等)，考虑到性能问题，因此hash统一由上层计算好传递进来。
 *
 * @author: carrypan
 * @date: 2019/09/09
 */
abstract class AbstractFileStorage<Credentials : ClientCredentials, Client>(
    private val locateStrategy: LocateStrategy,
    private val properties: StorageProperties
) : FileStorage {

    private var clientCache: LoadingCache<Credentials, Client>? = null

    init {
        if (properties.clientCache.enabled) {
            logger.info("Initializing storage client cache, size: [${properties.clientCache.size}].")
            clientCache = CacheBuilder.newBuilder()
                    .maximumSize(properties.clientCache.size)
                    .removalListener<Credentials, Client> { onClientRemoval(it.key, it.value) }
                    .build(object : CacheLoader<Credentials, Client>() {
                        override fun load(credentials: Credentials): Client = createClient(credentials)
                    })
        }
    }

    override fun store(hash: String, inputStream: InputStream, clientCredentials: ClientCredentials?) {
        val path = locateStrategy.locate(hash)
        val credentials = getOrDefaultCredentials(clientCredentials)

        try {
            inputStream.use {
                val client = getClient(credentials)
                if (exist(path, hash, client)) {
                    logger.debug("File [$hash] exists on [$credentials], skip store.")
                    return
                }
                store(path, hash, it, getClient(credentials))
                logger.info("Success to store file [$hash] on [$credentials].")
            }
        } catch (exception: Exception) {
            logger.error("Failed to store file [$hash] on [$credentials].", exception)
            throw StorageException("文件存储失败: ${exception.message}")
        }
    }

    override fun delete(hash: String, clientCredentials: ClientCredentials?) {
        val path = locateStrategy.locate(hash)
        val credentials = getOrDefaultCredentials(clientCredentials)

        try {
            delete(path, hash, getClient(credentials))
            logger.info("Success to remove file [$hash] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to remove file [$hash] on [$credentials].", exception)
            throw StorageException("文件删除失败: ${exception.message}")
        }
    }

    override fun load(hash: String, clientCredentials: ClientCredentials?): File? {
        val path = locateStrategy.locate(hash)
        val credentials = getOrDefaultCredentials(clientCredentials)

        return try {
            val file = load(path, hash, getClient(credentials))
            file?.let {
                logger.debug("Success to load file [$hash] on [$credentials].")
            } ?: logger.debug("Failed to load file [$hash] on [$credentials]: file does not exist.")
            file
        } catch (exception: Exception) {
            logger.error("Failed to load file [$hash] on [$credentials].", exception)
            throw StorageException("文件加载失败: ${exception.message}")
        }
    }

    override fun exist(hash: String, clientCredentials: ClientCredentials?): Boolean {
        val path = locateStrategy.locate(hash)
        val credentials = getOrDefaultCredentials(clientCredentials)
        return try {
            val exist = exist(path, hash, getClient(credentials))
            logger.debug("Check file [$hash] on [$credentials] exist: $exist.")
            exist
        } catch (exception: Exception) {
            logger.error("Failed to check file [$hash] exist on [$credentials].", exception)
            throw StorageException("文件查询失败: ${exception.message}")
        }
    }

    override fun append(path: String, filename: String, inputStream: InputStream, clientCredentials: ClientCredentials?) {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            append(path, filename, inputStream, getClient(credentials))
            logger.debug("Success to append file [$path/$filename] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to append file [$path/$filename] on [$credentials].", exception)
            throw StorageException("文件追加失败: ${exception.message}")
        }
    }

    override fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream, clientCredentials: ClientCredentials?) {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            storeBlock(path, sequence, sha256, inputStream, getClient(credentials))
            logger.debug("Success to store block file [$path/$sequence] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to store block file [$path/$sequence] on [$credentials].", exception)
            throw StorageException("文件分块存储失败: ${exception.message}")
        }
    }

    override fun combineBlock(path: String, clientCredentials: ClientCredentials?): File {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            val client = getClient(credentials)
            val file = combineBlock(path, client)
            logger.debug("Success to combine block file [$path] on [$credentials].")
            return file
        } catch (exception: Exception) {
            logger.error("Failed to combine block file [$path] on [$credentials].", exception)
            throw StorageException("文件合并失败: ${exception.message}")
        }
    }

    override fun listBlockInfo(path: String, clientCredentials: ClientCredentials?): List<Pair<Long, String>> {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            val client = getClient(credentials)
            val blockList = listBlockInfo(path, client)
            logger.debug("Success to list block file [$path] on [$credentials].")
            return blockList
        } catch (exception: Exception) {
            logger.error("Failed to list block file [$path] on [$credentials].", exception)
            throw StorageException("分块文件列表失败: ${exception.message}")
        }
    }

    override fun makeBlockPath(path: String, clientCredentials: ClientCredentials?) {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            val client = getClient(credentials)
            makeBlockPath(path, client)
            logger.debug("Success to make block path [$path] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to make block path [$path] on [$credentials].", exception)
            throw StorageException("创建分块目录失败: ${exception.message}")
        }
    }

    override fun checkBlockPath(path: String, clientCredentials: ClientCredentials?): Boolean {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            val client = getClient(credentials)
            val exist = checkBlockPath(path, client)
            logger.debug("Success to check block path [$path] on [$credentials]: $exist.")
            return exist
        } catch (exception: Exception) {
            logger.error("Failed to check block path [$path] on [$credentials].", exception)
            throw StorageException("检查分块目录失败: ${exception.message}")
        }
    }

    override fun deleteBlockPath(path: String, clientCredentials: ClientCredentials?) {
        val credentials = getOrDefaultCredentials(clientCredentials)
        try {
            val client = getClient(credentials)
            deleteBlockPath(path, client)
            logger.debug("Success to delete block path [$path] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to delete block path [$path] on [$credentials].", exception)
            throw StorageException("删除分块目录失败: ${exception.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getClient(clientCredentials: ClientCredentials): Client {
        val credentials = clientCredentials as Credentials
        return clientCache?.get(credentials) ?: createClient(credentials)
    }

    private fun getOrDefaultCredentials(clientCredentials: ClientCredentials?): ClientCredentials {
        return clientCredentials ?: properties.credentials
    }

    protected open fun onClientRemoval(credentials: Credentials, client: Client) {
        logger.debug("Cached storage client is removed")
    }

    protected abstract fun createClient(credentials: Credentials): Client
    protected abstract fun store(path: String, filename: String, inputStream: InputStream, client: Client)
    protected abstract fun delete(path: String, filename: String, client: Client)
    protected abstract fun load(path: String, filename: String, client: Client): File?
    protected abstract fun exist(path: String, filename: String, client: Client): Boolean
    protected abstract fun append(path: String, filename: String, inputStream: InputStream, client: Client)
    protected abstract fun makeBlockPath(path: String, client: Client)
    protected abstract fun checkBlockPath(path: String, client: Client): Boolean
    protected abstract fun deleteBlockPath(path: String, client: Client)
    protected abstract fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream, client: Client)
    protected abstract fun combineBlock(path: String, client: Client): File
    protected abstract fun listBlockInfo(path: String, client: Client): List<Pair<Long, String>>

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileStorage::class.java)
    }
}
