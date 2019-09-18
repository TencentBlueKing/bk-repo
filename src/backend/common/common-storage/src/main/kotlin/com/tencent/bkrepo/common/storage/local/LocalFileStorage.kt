package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
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
        defaultCredentials: LocalStorageCredentials
) : AbstractFileStorage<LocalStorageCredentials, LocalStorageClient>(locateStrategy, defaultCredentials) {
    override fun store(path: String, filename: String, inputStream: InputStream, client: LocalStorageClient) {
        client.store(path, filename, inputStream)
    }

    override fun delete(path: String, filename: String, client: LocalStorageClient) {
        client.delete(path, filename)
    }

    override fun load(path: String, filename: String, client: LocalStorageClient): InputStream? {
        return client.load(path, filename)
    }

    override fun exist(path: String, filename: String, client: LocalStorageClient): Boolean {
        return client.exist(path, filename)
    }

    override fun createClient(key: LocalStorageCredentials) = LocalStorageClient(key.directory)


}
