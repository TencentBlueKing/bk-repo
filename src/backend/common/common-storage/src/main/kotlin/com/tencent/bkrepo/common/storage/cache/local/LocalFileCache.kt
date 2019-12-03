package com.tencent.bkrepo.common.storage.cache.local

import com.tencent.bkrepo.common.storage.cache.AbstractFileCache
import com.tencent.bkrepo.common.storage.local.LocalStorageClient
import com.tencent.bkrepo.common.storage.schedule.CleanupResult
import java.io.File
import java.io.InputStream

/**
 * 本地文件缓存
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
class LocalFileCache(properties: LocalFileCacheProperties) : AbstractFileCache() {

    private val cachedExpires = properties.expires
    private val client = LocalStorageClient(properties.path)


    override fun doCache(path: String, filename: String, inputStream: InputStream): File {
        return client.store(path, filename, inputStream)
    }

    override fun doGet(path: String, filename: String): File? {
        return client.load(path, filename)
    }

    override fun doRemove(path: String, filename: String) {
        return client.delete(path, filename)
    }

    override fun doTouch(path: String, filename: String): File {
        return client.touch(path, filename)
    }

    override fun checkExist(path: String, filename: String): Boolean {
        return client.exist(path, filename)
    }

    override fun doAppend(path: String, filename: String, inputStream: InputStream) {
        return client.append(path, filename, inputStream)
    }

    override fun doMakeBlockPath(path: String) {
        return client.makeBlockPath(path)
    }

    override fun doCheckBlockPath(path: String): Boolean {
        return client.checkBlockPath(path)
    }

    override fun doDeleteBlockPath(path: String) {
        return client.deleteBlockPath(path)
    }

    override fun listBlockInfo(path: String): List<Pair<Long, String>> {
        return client.listBlockInfo(path)
    }

    override fun doStoreBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream) {
        return client.storeBlock(path, sequence, sha256, inputStream)
    }

    override fun doCombineBlock(path: String): File {
        return client.combineBlock(path)
    }

    override fun onClean(): CleanupResult {
        return client.cleanup(cachedExpires)
    }
}
