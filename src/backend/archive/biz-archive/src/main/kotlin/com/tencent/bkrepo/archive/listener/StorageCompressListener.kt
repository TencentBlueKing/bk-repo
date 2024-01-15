package com.tencent.bkrepo.archive.listener

import com.tencent.bkrepo.archive.constant.DEFAULT_KEY
import com.tencent.bkrepo.archive.event.StorageFileCompressedEvent
import com.tencent.bkrepo.archive.event.StorageFileUncompressedEvent
import com.tencent.bkrepo.archive.metrics.ArchiveMetrics
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.HumanReadable
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 存储压缩事件监听器
 * */
@Component
class StorageCompressListener(val archiveMetrics: ArchiveMetrics) {
    /**
     * 压缩存储文件
     * */
    @EventListener(StorageFileCompressedEvent::class)
    fun compress(event: StorageFileCompressedEvent) {
        val key = event.storageCredentialsKey ?: DEFAULT_KEY
        with(event) {
            logger.info("Success to compress file $sha256 on $storageCredentialsKey,$throughput.")
            if (compressed != -1L) {
                val ratio = StringPool.calculateRatio(uncompressed, compressed)
                val uncompressedSize = HumanReadable.size(uncompressed)
                val compressedSize = HumanReadable.size(compressed)
                val freeSize = uncompressed - compressed
                val compressInfo = "$uncompressedSize->$compressedSize,${HumanReadable.size(freeSize)},ratio:$ratio"
                logger.info("File[$sha256] compress info: $compressInfo.")
                // 释放存储
                archiveMetrics.getSizeCounter(ArchiveMetrics.Action.STORAGE_FREE, key).increment(freeSize.toDouble())
            }
            archiveMetrics.getCounter(ArchiveMetrics.Action.COMPRESSED, key).increment() // 压缩个数
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.COMPRESSED, key)
                .increment(throughput.bytes.toDouble()) // 压缩吞吐
            archiveMetrics.getTimer(ArchiveMetrics.Action.COMPRESSED, key).record(throughput.duration) // 压缩时长
        }
    }

    /**
     * 解压存储文件
     * */
    @EventListener(StorageFileUncompressedEvent::class)
    fun uncompress(event: StorageFileUncompressedEvent) {
        with(event) {
            logger.info("Success to uncompress file $sha256 on $storageCredentialsKey,$throughput")
            val key = event.storageCredentialsKey ?: DEFAULT_KEY
            val allocateSize = uncompressed - compressed
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.STORAGE_ALLOCATE, key)
                .increment(allocateSize.toDouble()) // 新增存储
            archiveMetrics.getCounter(ArchiveMetrics.Action.UNCOMPRESSED, key).increment() // 解压个数
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.UNCOMPRESSED, key)
                .increment(throughput.bytes.toDouble()) // 解压吞吐
            archiveMetrics.getTimer(ArchiveMetrics.Action.UNCOMPRESSED, key).record(throughput.duration) // 解压时长
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageCompressListener::class.java)
    }
}
