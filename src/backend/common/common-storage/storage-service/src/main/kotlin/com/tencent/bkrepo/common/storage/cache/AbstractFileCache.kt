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
        logger.debug("File [$filename] has been cached.")
        return file
    }

    override fun get(path: String, filename: String): File? {
        val file = doGet(path, filename)
        file?.let {
            logger.debug("Cache file [$filename] is hit.")
        } ?: logger.debug("Cache file [$filename] is miss.")
        return file
    }

    override fun remove(path: String, filename: String) {
        doRemove(path, filename)
        logger.debug("Cache File [$filename] has been removed.")
    }

    override fun touch(path: String, filename: String): File {
        val file = doTouch(path, filename)
        logger.debug("Cache File [$filename] has been created.")
        return file
    }

    override fun exist(path: String, filename: String): Boolean {
        val exist = checkExist(path, filename)
        logger.debug("Check cached file [$filename] exist: [$exist].")
        return exist
    }

    override fun append(path: String, filename: String, inputStream: InputStream) {
        doAppend(path, filename, inputStream)
        logger.debug("Append cached block file [$path/$filename] success.")
    }

    override fun makeBlockPath(path: String) {
        doMakeBlockPath(path)
        logger.debug("Make cached block path [$path] success.")
    }

    override fun checkBlockPath(path: String): Boolean {
        val exist = doCheckBlockPath(path)
        logger.debug("Check cached path [$path] exist: [$exist].")
        return exist
    }

    override fun deleteBlockPath(path: String) {
        doDeleteBlockPath(path)
        logger.debug("Delete cached path [$path] success.")
    }

    override fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream) {
        doStoreBlock(path, sequence, sha256, inputStream)
        logger.debug("Store cached block file [$path/$sequence] success.")
    }

    override fun combineBlock(path: String): File {
        val file = doCombineBlock(path)
        logger.debug("Combine cached block file [$path] success.")
        return file
    }

    abstract fun doCache(path: String, filename: String, inputStream: InputStream): File
    abstract fun doGet(path: String, filename: String): File?
    abstract fun doRemove(path: String, filename: String)
    abstract fun doTouch(path: String, filename: String): File
    abstract fun checkExist(path: String, filename: String): Boolean
    abstract fun doAppend(path: String, filename: String, inputStream: InputStream)
    abstract fun doStoreBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream)
    abstract fun doCombineBlock(path: String): File
    abstract fun doMakeBlockPath(path: String)
    abstract fun doCheckBlockPath(path: String): Boolean
    abstract fun doDeleteBlockPath(path: String)

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileCache::class.java)
    }
}
