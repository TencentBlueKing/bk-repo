package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.pojo.LocalStorageCredentials
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream

/**
 * 本地文件存储
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class LocalFileStorage(
    locateStrategy: LocateStrategy,
    properties: LocalStorageProperties
) : AbstractFileStorage<LocalStorageCredentials, LocalStorageClient>(locateStrategy, properties) {

    override fun createClient(credentials: LocalStorageCredentials) = LocalStorageClient(credentials.path)

    override fun store(path: String, filename: String, inputStream: InputStream, client: LocalStorageClient) {
        client.store(path, filename, inputStream)
    }

    override fun delete(path: String, filename: String, client: LocalStorageClient) {
        client.delete(path, filename)
    }

    override fun load(path: String, filename: String, client: LocalStorageClient): File? {
        return client.load(path, filename)
    }

    override fun exist(path: String, filename: String, client: LocalStorageClient): Boolean {
        return client.exist(path, filename)
    }

    override fun append(path: String, filename: String, inputStream: InputStream, client: LocalStorageClient) {
        client.append(path, filename, inputStream)
    }

    override fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream, client: LocalStorageClient) {
        client.storeBlock(path, sequence, sha256, inputStream)
    }

    override fun combineBlock(path: String, client: LocalStorageClient): File {
        return client.combineBlock(path)
    }

    override fun makeBlockPath(path: String, client: LocalStorageClient) {
        return client.makeBlockPath(path)
    }

    override fun checkBlockPath(path: String, client: LocalStorageClient): Boolean {
        return client.checkBlockPath(path)
    }

    override fun deleteBlockPath(path: String, client: LocalStorageClient) {
        return client.deleteBlockPath(path)
    }

    override fun listBlockInfo(path: String, client: LocalStorageClient): List<Pair<Long, String>> {
        return client.listBlockInfo(path)
    }
}
