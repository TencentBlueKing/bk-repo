package com.tencent.bkrepo.common.storage.cache

import java.io.File
import java.io.InputStream
import org.slf4j.LoggerFactory

/**
 * 抽象文件缓存
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
abstract class AbstractFileCache : FileCache {

    override fun cache(path: String, filename: String, inputStream: InputStream): File {
        val file = doCache(path, filename, inputStream)
        logger.debug("File [$filename] has been cached")
        return file
    }

    override fun get(path: String, filename: String): File? {
        val file = doGet(path, filename)
        file?.let {
            logger.debug("Cache file [$filename] is hit")
        } ?: logger.debug("Cache file [$filename] is miss")
        return file
    }

    override fun remove(path: String, filename: String) {
        doRemove(path, filename)
        logger.debug("Cache File [$filename] has been removed")
    }

    override fun touch(path: String, filename: String): File {
        val file = doTouch(path, filename)
        logger.debug("Cache File [$filename] has been created")
        return file
    }

    override fun exist(path: String, filename: String): Boolean {
        val exist = checkExist(path, filename)
        logger.debug("Check cached file [$filename] exist: $exist")
        return exist
    }

    protected fun isExpired(file: File, expireSeconds: Long): Boolean {
        val isFile = file.isFile
        val isExpired = (System.currentTimeMillis() - file.lastModified()) >= expireSeconds * 1000
        return isFile && isExpired
    }

    abstract fun doCache(path: String, filename: String, inputStream: InputStream): File
    abstract fun doGet(path: String, filename: String): File?
    abstract fun doRemove(path: String, filename: String)
    abstract fun doTouch(path: String, filename: String): File
    abstract fun checkExist(path: String, filename: String): Boolean

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileCache::class.java)
    }
}
