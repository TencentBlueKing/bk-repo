package com.tencent.bkrepo.common.storage.cache.local

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.common.storage.cache.AbstractFileCache
import com.tencent.bkrepo.common.storage.cache.FileCache
import java.io.File
import java.io.InputStream

/**
 * 本地文件缓存
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
class LocalFileCache(properties: LocalFileCacheProperties) : AbstractFileCache() {

    private val cachedPath = properties.path
    private val cachedExpires = properties.expires

    init {
        val directory = File(cachedPath)
        directory.mkdirs()
    }

    override fun doCache(path: String, filename: String, inputStream: InputStream): File {
        val file = File(cachedPath, filename)
        if (!file.exists()) {
            file.outputStream().use {
                ByteStreams.copy(inputStream, it)
            }
        }
        return file
    }

    override fun doGet(path: String, filename: String): File? {
        val file = File(cachedPath, filename)
        return if (file.exists() && file.isFile) file else null
    }

    override fun doRemove(path: String, filename: String) {
        val file = File(cachedPath, filename)
        file.takeIf { it.exists() && it.isAbsolute }?.run { this.delete() }
    }

    override fun doTouch(path: String, filename: String): File {
        return File(cachedPath, filename)
    }

    override fun checkExist(path: String, filename: String): Boolean {
        val file = File(cachedPath, filename)
        return file.exists() && file.isFile
    }

    override fun onClean(): FileCache.CleanResult {
        val directory = File(cachedPath)
        val files = directory.listFiles() ?: arrayOf()
        var count = 0L
        var size = 0L
        files.forEach {
            if (it.isFile && isExpired(it, cachedExpires)) {
                val fileSize = it.length()
                if (it.delete()) {
                    count += 1
                    size += fileSize
                }
            }
        }

        return FileCache.CleanResult(count, size)
    }
}
