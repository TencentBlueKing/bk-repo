package com.tencent.bkrepo.archive.listener

import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.event.FileCompressedEvent
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.metrics.ArchiveMetrics
import java.text.DecimalFormat
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 文件归档业务监听器
 * */
@Component
class ArchiveListener(val archiveMetrics: ArchiveMetrics) {

    /**
     * 压缩比格式
     * */
    private val df = DecimalFormat("#.#")

    @EventListener(FileArchivedEvent::class)
    fun archive(event: FileArchivedEvent) {
        with(event) {
            logger.info("Archive file $sha256 on $storageCredentialsKey, $throughput.")
            val key = storageCredentialsKey ?: DEFAULT_KEY
            archiveMetrics.getCounter(ArchiveMetrics.Action.ARCHIVED, key).increment()
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.ARCHIVED, key).increment(throughput.bytes.toDouble())
            archiveMetrics.getTimer(ArchiveMetrics.Action.ARCHIVED, key).record(throughput.duration)
        }
    }

    @EventListener(FileRestoredEvent::class)
    fun restore(event: FileRestoredEvent) {
        with(event) {
            logger.info("Restore file $sha256 on $storageCredentialsKey, $throughput.")
            val key = event.storageCredentialsKey ?: DEFAULT_KEY
            archiveMetrics.getCounter(ArchiveMetrics.Action.RESTORED, key).increment()
            archiveMetrics.getSizeCounter(ArchiveMetrics.Action.RESTORED, key).increment(throughput.bytes.toDouble())
            archiveMetrics.getTimer(ArchiveMetrics.Action.RESTORED, key).record(throughput.duration)
        }
    }

    @EventListener(FileCompressedEvent::class)
    fun compress(event: FileCompressedEvent) {
        with(event) {
            val ratio = df.format((uncompressed - compressed.toDouble()) / uncompressed * 100)
            logger.info("Compress file $sha256, compressed:$compressed,uncompressed:$uncompressed,ratio:$ratio")
            archiveMetrics.getCompressSizeCount(ArchiveMetrics.CompressCounterType.COMPRESSED.name)
                .increment(compressed.toDouble())
            archiveMetrics.getCompressSizeCount(ArchiveMetrics.CompressCounterType.UNCOMPRESSED.name)
                .increment(uncompressed.toDouble())
            archiveMetrics.getCompressTimer().record(throughput.duration)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveListener::class.java)
        private const val DEFAULT_KEY = "default"
    }
}
