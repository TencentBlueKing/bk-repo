package com.tencent.bkrepo.generic.util

import com.google.common.io.ByteStreams
import org.apache.commons.io.IOUtils
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
        val fileOutputStream = cacheFile.outputStream()
        try {
            return Pair(cacheFile.absolutePath, IOUtils.copyLarge(inputStream, fileOutputStream))
        } finally {
            IOUtils.closeQuietly(inputStream)
            IOUtils.closeQuietly(fileOutputStream)
        }
    }

    fun deleteFile(fullPath: String) {
        File(fullPath).delete()
    }
}