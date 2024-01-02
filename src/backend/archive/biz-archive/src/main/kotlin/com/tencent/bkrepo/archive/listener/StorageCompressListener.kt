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
        with(event) {
            val ratio = StringPool.calculateRatio(uncompressed, compressed)
            val uncompressedSize = HumanReadable.size(uncompressed)
            val compressedSize = HumanReadable.size(compressed)
            val releaseSize = HumanReadable.size(uncompressed - compressed)
            logger.info(
                "Success to compress file $sha256 on $storageCredentialsKey," +
                    "($uncompressedSize->$compressedSize,$releaseSize) ratio:$ratio ,$throughput",
            )
            val key = event.storageCredentialsKey ?: DEFAULT_KEY
            archiveMetrics.getCounter(ArchiveMetrics.Action.COMPRESSED, key).increment()
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.COMPRESSED, key, TYPE, TAG_COMPRESSED)
                .increment(compressed.toDouble())
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.COMPRESSED, key, TYPE, TAG_UNCOMPRESSED)
                .increment(uncompressed.toDouble())
            archiveMetrics.getTimer(ArchiveMetrics.Action.COMPRESSED, key).record(throughput.duration)
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
            archiveMetrics.getCounter(ArchiveMetrics.Action.UNCOMPRESSED, key).increment()
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.UNCOMPRESSED, key, TYPE, TAG_COMPRESSED)
                .increment(compressed.toDouble())
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.UNCOMPRESSED, key, TYPE, TAG_UNCOMPRESSED)
                .increment(uncompressed.toDouble())
            archiveMetrics.getTimer(ArchiveMetrics.Action.UNCOMPRESSED, key).record(throughput.duration)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageCompressListener::class.java)
        private const val TYPE = "type"
        private const val TAG_COMPRESSED = "compressed"
        private const val TAG_UNCOMPRESSED = "uncompressed"
    }
}
