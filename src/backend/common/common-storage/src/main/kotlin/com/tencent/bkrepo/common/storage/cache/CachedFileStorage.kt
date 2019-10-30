package com.tencent.bkrepo.common.storage.cache

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.core.ClientCredentials
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async

/**

 * 封装了身份信息和存储客户端的缓存机制
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
abstract class CachedFileStorage<Credentials : ClientCredentials, Client>(
    private val fileCache: FileCache,
    locateStrategy: LocateStrategy,
    properties: StorageProperties
) : AbstractFileStorage<Credentials, Client>(locateStrategy, properties) {

    override fun store(path: String, filename: String, inputStream: InputStream, client: Client) {
        val cachedFile = fileCache.cache(path, filename, inputStream)
        doStore(path, filename, cachedFile, client)
    }

    override fun delete(path: String, filename: String, client: Client) {
        fileCache.remove(path, filename)
        doDelete(path, filename, client)
    }

    override fun load(path: String, filename: String, client: Client): File? {
        return fileCache.get(path, filename) ?: run {
            val cachedFile = fileCache.touch(path, filename)
            doLoad(path, filename, cachedFile, client)
        }
    }

    override fun exist(path: String, filename: String, client: Client): Boolean {
        return fileCache.exist(path, filename) || checkExist(path, filename, client)
    }

    @Async
    abstract fun doStore(path: String, filename: String, cachedFile: File, client: Client)
    @Async
    abstract fun doDelete(path: String, filename: String, client: Client)
    abstract fun doLoad(path: String, filename: String, file: File, client: Client): File?
    abstract fun checkExist(path: String, filename: String, client: Client): Boolean

    companion object {
        private val logger = LoggerFactory.getLogger(CachedFileStorage::class.java)
    }
}
