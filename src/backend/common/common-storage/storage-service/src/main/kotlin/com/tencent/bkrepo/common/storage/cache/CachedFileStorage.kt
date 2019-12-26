package com.tencent.bkrepo.common.storage.cache

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.pojo.StorageCredentials
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream
import org.springframework.scheduling.annotation.Async

/**

 * 封装了身份信息和存储客户端的缓存机制
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
abstract class CachedFileStorage<Credentials : StorageCredentials, Client>(
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
        return checkExist(path, filename, client)
    }

    override fun append(path: String, filename: String, inputStream: InputStream, client: Client) {
        fileCache.append(path, filename, inputStream)
    }

    override fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream, client: Client) {
        fileCache.storeBlock(path, sequence, sha256, inputStream)
    }

    override fun combineBlock(path: String, client: Client): File {
        return fileCache.combineBlock(path)
    }

    override fun listBlockInfo(path: String, client: Client): List<Pair<Long, String>> {
        return fileCache.listBlockInfo(path)
    }

    override fun makeBlockPath(path: String, client: Client) {
        return fileCache.makeBlockPath(path)
    }

    override fun checkBlockPath(path: String, client: Client): Boolean {
        return fileCache.checkBlockPath(path)
    }

    override fun deleteBlockPath(path: String, client: Client) {
        return fileCache.deleteBlockPath(path)
    }

    @Async
    abstract fun doStore(path: String, filename: String, cachedFile: File, client: Client)
    @Async
    abstract fun doDelete(path: String, filename: String, client: Client)
    abstract fun doLoad(path: String, filename: String, file: File, client: Client): File?
    abstract fun checkExist(path: String, filename: String, client: Client): Boolean
}
