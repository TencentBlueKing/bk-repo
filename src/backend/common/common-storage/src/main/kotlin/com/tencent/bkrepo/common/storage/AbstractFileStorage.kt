package com.tencent.bkrepo.common.storage

import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.InputStream

/**
 * 文件存储抽象类
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
abstract class AbstractFileStorage(
    private val locateStrategy: LocateStrategy
) : FileStorage {

    override fun store(hash: String, inputStream: InputStream) {
        val path = locateStrategy.locate(hash)
        store(hash, path, inputStream)
    }

    override fun delete(hash: String) {
        val path = locateStrategy.locate(hash)
        delete(path, hash)
    }

    override fun load(hash: String): InputStream {
        val path = locateStrategy.locate(hash)
        return load(path, hash)
    }

    override fun exist(hash: String): Boolean {
        val path = locateStrategy.locate(hash)
        return exist(path, hash)
    }

    abstract fun store(filename: String, path: String, inputStream: InputStream)
    abstract fun delete(filename: String, path: String)
    abstract fun load(filename: String, path: String): InputStream
    abstract fun exist(filename: String, path: String): Boolean
}
