package com.tencent.bkrepo.common.storage.core

import com.google.common.io.ByteStreams
import java.io.File
import java.io.InputStream

/**
 * 本地文件缓存
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
class LocalFileCache(private val cachePath: String) {

    fun cache(path: String, filename: String, inputStream: InputStream): File {
        val subDirectory = File(cachePath, path)
        subDirectory.mkdirs()
        val file = File(subDirectory, filename)
        val outputStream = file.outputStream()
        ByteStreams.copy(inputStream, outputStream)
        outputStream.close()
        return file
    }

    fun get(path: String, filename: String): File? {
        val subDirectory = File(cachePath, path)
        val file = File(subDirectory, filename)
        return if (file.exists() && file.isFile) file else null
    }

    fun remove(path: String, filename: String) {
        val subDirectory = File(cachePath, path)
        val file = File(subDirectory, filename)
        if (file.exists() && file.isFile) {
            file.delete()
        }
    }
}
