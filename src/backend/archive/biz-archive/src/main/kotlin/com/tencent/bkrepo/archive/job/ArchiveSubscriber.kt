package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.monitor.Throughput
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.SignalType

/**
 * 归档任务订阅者
 * */
class ArchiveSubscriber(val archiveFileRepository: ArchiveFileRepository) : BaseSubscriber<ArchiveFileWrapper>() {
    /**
     * 开始时间
     * */
    private var startAt: Long = -1L

    /**
     * 任务上下文
     * */
    private val jobContext = JobContext()

    /**
     * 闭锁，用于同步任务
     * */
    private val countDownLatch = CountDownLatch(1)
    override fun hookOnSubscribe(subscription: Subscription) {
        logger.info("Start execute archive job.")
        ArchiveUtils.monitor.addMonitor(MONITOR_ID, jobContext)
        startAt = System.currentTimeMillis()
        super.hookOnSubscribe(subscription)
    }

    override fun hookOnNext(fileWrapper: ArchiveFileWrapper) {
        jobContext.total.incrementAndGet()
        val archiveFile = fileWrapper.archiveFile
        jobContext.totalSize.addAndGet(archiveFile.size)
        if (fileWrapper.throwable != null) {
            // error
            logger.error("Archive file failed: ", fileWrapper.throwable)
            jobContext.failed.incrementAndGet()
            updateArchiveFile(archiveFile, ArchiveStatus.ARCHIVE_FAILED)
        } else {
            // success
            logger.info("Archive file(${archiveFile.key()}) successful")
            jobContext.success.incrementAndGet()
            val nanos = Duration.between(fileWrapper.startTime, LocalDateTime.now()).toNanos()
            val tp = Throughput(archiveFile.size, nanos)
            val event = FileArchivedEvent(archiveFile.sha256, archiveFile.storageCredentialsKey, tp)
            SpringContextUtils.publishEvent(event)
            updateArchiveFile(archiveFile, ArchiveStatus.ARCHIVED)
        }
    }

    override fun hookOnComplete() {
        val stopAt = System.currentTimeMillis()
        val throughput = Throughput(jobContext.totalSize.get(), stopAt - startAt, ChronoUnit.MILLIS)
        logger.info("Archive job execute successful.summary: $jobContext $throughput.")
    }

    override fun hookOnError(throwable: Throwable) {
        logger.error("Archive job execute successful failed: ", throwable)
    }

    override fun hookFinally(type: SignalType) {
        ArchiveUtils.monitor.removeMonitor(MONITOR_ID)
        countDownLatch.countDown()
    }

    fun block() {
        countDownLatch.await()
    }

    /**
     * 更新文件状态
     * */
    private fun updateArchiveFile(file: TArchiveFile, status: ArchiveStatus) {
        file.lastModifiedDate = LocalDateTime.now()
        file.status = status
        archiveFileRepository.save(file)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveSubscriber::class.java)
        private const val MONITOR_ID = "archive-job"
    }
}
