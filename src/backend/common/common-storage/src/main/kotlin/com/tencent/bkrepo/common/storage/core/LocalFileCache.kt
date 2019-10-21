package com.tencent.bkrepo.common.storage.core

import com.google.common.io.ByteStreams
import java.io.File
import java.io.InputStream
import org.slf4j.LoggerFactory

/**
 * 本地文件缓存
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
class LocalFileCache(private val cachePath: String) {

    init {
        val directory = File(cachePath)
        directory.mkdirs()
    }

    fun cache(filename: String, inputStream: InputStream): File {
        val file = File(cachePath, filename)
        val outputStream = file.outputStream()
        ByteStreams.copy(inputStream, outputStream)
        outputStream.close()
        logger.debug("File $filename has been cached in local.")
        return file
    }

    fun get(filename: String): File? {
        val file = File(cachePath, filename)
        return if (file.exists() && file.isFile) {
            logger.debug("Cached file $filename is hit.")
            file
        } else null
    }

    fun remove(filename: String) {
        val file = File(cachePath, filename)
        if (file.exists() && file.isFile) {
            file.delete()
            logger.debug("File $filename has been removed in local cache.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LocalFileCache::class.java)
    }
}
