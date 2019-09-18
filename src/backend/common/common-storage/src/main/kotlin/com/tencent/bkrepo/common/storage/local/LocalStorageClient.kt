package com.tencent.bkrepo.common.storage.local

import com.google.common.io.ByteStreams
import java.io.File
import java.io.InputStream

/**
 * 本地文件存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-18
 */
class LocalStorageClient(private val directory: String) {

    init{
        val dir = File(directory)
        dir.mkdirs()
        assert(dir.isFile) {"$directory is not a valid directory path!"}
    }

    fun store(path: String, filename: String, inputStream: InputStream) {
        val subDirectory = File(directory, path)
        subDirectory.mkdirs()
        val out = File(subDirectory, filename).outputStream()
        ByteStreams.copy(inputStream, out)
        out.close()
    }

    fun delete(path: String, filename: String) {
        val subDirectory = File(directory, path)
        val file = File(subDirectory, filename)
        if(file.isFile) {
            file.deleteOnExit()
        }
    }

    fun load(path: String, filename: String): InputStream? {
        val subDirectory = File(directory, path)
        val file = File(subDirectory, filename)
        return if(file.isFile) file.inputStream() else null
    }

    fun exist(path: String, filename: String): Boolean {
        val subDirectory = File(directory, path)
        val file = File(subDirectory, filename)
        return file.isFile
    }
}
