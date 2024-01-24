package com.tencent.bkrepo.archive.listener

import com.tencent.bkrepo.archive.constant.DEFAULT_KEY
import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.event.FileCompressedEvent
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.metrics.ArchiveMetrics
import com.tencent.bkrepo.common.api.constant.StringPool
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 文件归档业务监听器
 * */
@Component
class ArchiveListener(val archiveMetrics: ArchiveMetrics) {

    /**
     * 归档文件事件
     * */
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

    /**
     * 归档文件恢复事件
     * */
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

    /**
     * 归档文件压缩事件
     * */
    @EventListener(FileCompressedEvent::class)
    fun compress(event: FileCompressedEvent) {
        with(event) {
            val ratio = StringPool.calculateRatio(uncompressed, compressed)
            logger.info(
                "Archive utils compress file $sha256,compressed:$compressed," +
                    "uncompressed:$uncompressed,ratio:$ratio, $throughput",
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveListener::class.java)
    }
}
