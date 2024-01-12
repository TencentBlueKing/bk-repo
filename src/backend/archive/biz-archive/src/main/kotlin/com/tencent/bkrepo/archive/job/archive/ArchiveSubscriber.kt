package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.job.BaseJobSubscriber
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.monitor.Throughput
import java.time.Duration
import java.time.LocalDateTime
import org.slf4j.LoggerFactory

/**
 * 归档任务订阅者
 * */
class ArchiveSubscriber(
    private val archiveFileRepository: ArchiveFileRepository,
) : BaseJobSubscriber<ArchiveFileWrapper>() {

    override fun doOnNext(value: ArchiveFileWrapper) {
        with(value) {
            jobContext.totalSize.addAndGet(archiveFile.size)
            throwable?.let {
                // error
                logger.error("Archive file failed: ", throwable)
                updateArchiveFile(archiveFile, ArchiveStatus.ARCHIVE_FAILED)
                throw it
            }
            // success
            logger.info("Archive file(${archiveFile.key()}) successful")
            val nanos = Duration.between(startTime, LocalDateTime.now()).toNanos()
            val tp = Throughput(archiveFile.size, nanos)
            val event = FileArchivedEvent(archiveFile.sha256, archiveFile.storageCredentialsKey, tp)
            SpringContextUtils.publishEvent(event)
            updateArchiveFile(archiveFile, ArchiveStatus.ARCHIVED)
        }
    }

    /**
     * 更新文件状态
     * */
    private fun updateArchiveFile(file: TArchiveFile, status: ArchiveStatus) {
        file.lastModifiedDate = LocalDateTime.now()
        file.status = status
        archiveFileRepository.save(file)
    }

    override fun getSize(value: ArchiveFileWrapper): Long {
        return value.archiveFile.size
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveSubscriber::class.java)
    }
}
