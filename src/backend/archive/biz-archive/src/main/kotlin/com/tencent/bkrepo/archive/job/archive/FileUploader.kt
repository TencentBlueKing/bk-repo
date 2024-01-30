package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.constant.DEEP_ARCHIVE
import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import java.nio.file.Files
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * 文件上传器
 * */
class FileUploader(
    /**
     * 上传文件的目标cos
     * */
    private val cosClient: CosClient,
) : AbstractArchiveFileWrapperCallback() {
    override fun process(fileWrapper: ArchiveFileWrapper): Mono<ArchiveFileWrapper> {
        return Mono.create {
            // 如果没有这是压缩文件，则跳过上传
            if (fileWrapper.compressedFilePath != null) {
                upload(fileWrapper)
            }
            it.success(fileWrapper)
        }
    }

    private fun upload(
        fileWrapper: ArchiveFileWrapper,
    ) {
        val filePath = fileWrapper.compressedFilePath!!
        with(fileWrapper.archiveFile) {
            val key = "$sha256$XZ_SUFFIX"
            try {
                // 存储压缩文件
                val throughput = measureThroughput(Files.size(filePath)) {
                    cosClient.putFileObject(key, filePath.toFile(), DEEP_ARCHIVE)
                }
                logger.info("Success upload $key,$throughput.")
            } finally {
                Files.deleteIfExists(filePath)
                logger.info("Delete compressed file $filePath")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileUploader::class.java)
    }
}
