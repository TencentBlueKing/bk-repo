package com.tencent.bkrepo.generic.util

import com.google.common.io.ByteStreams
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*

object UploadFileStoreUtils {
    private const val FILE_STORE_DIR = "/data/image-upload-fileStore"

    init {
        File(FILE_STORE_DIR).mkdirs()
    }

    fun storeFile(inputStream: InputStream): Pair<String, Long> {
        val cacheFile = File(FILE_STORE_DIR, UUID.randomUUID().toString())
        cacheFile.outputStream().use {
            return Pair(cacheFile.absolutePath, ByteStreams.copy(inputStream, it))
        }
    }

    fun deleteFile(fullPath: String) {
        File(fullPath).delete()
    }
}