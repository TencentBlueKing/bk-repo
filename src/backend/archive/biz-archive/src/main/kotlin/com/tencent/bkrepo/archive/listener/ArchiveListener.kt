package com.tencent.bkrepo.archive.listener

import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.metrics.ArchiveMetrics
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 文件归档业务监听器
 * */
@Component
class ArchiveListener(val archiveMetrics: ArchiveMetrics) {

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

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveListener::class.java)
        private const val DEFAULT_KEY = "default"
    }
}
