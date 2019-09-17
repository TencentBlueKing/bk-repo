package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream

/**
 * CephFS文件存储
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class LocalFileStorage(locateStrategy: LocateStrategy, localStorageProperties: LocalStorageProperties) : AbstractFileStorage(locateStrategy) {

    private val directory: String = localStorageProperties.directory

    init {
        val localPath = File(directory)
        if(!localPath.exists()) {
            localPath.mkdirs()
        }
        assert(localPath.isDirectory) {"$directory is not a directory"}
    }

    override fun store(path: String, filename: String, inputStream: InputStream) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(path: String, filename: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun load(path: String, filename: String): InputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun exist(path: String, filename: String): Boolean {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
