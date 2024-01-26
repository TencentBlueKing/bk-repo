package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.api.stream.ChunkedFutureListener
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Future
import org.apache.commons.logging.LogFactory

class FileCleanupChunkedFutureListener : ChunkedFutureListener<File> {
    override fun done(future: Future<File>?, getInputStreamTime: Long) {
        if (future == null || future.isCancelled) {
            return
        }
        val file = future.get()
        try {
            Files.deleteIfExists(file.toPath())
            logger.debug("Delete cos downloading temp file[$file] success.")
        } catch (e: Exception) {
            logger.error("Delete cos downloading temp file[$file] failed.")
        }
    }

    companion object {
        private val logger = LogFactory.getLog(FileCleanupChunkedFutureListener::class.java)
    }
}
