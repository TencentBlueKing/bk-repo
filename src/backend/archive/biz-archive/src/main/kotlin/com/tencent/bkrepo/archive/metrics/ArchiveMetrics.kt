package com.tencent.bkrepo.archive.metrics

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.job.ReactiveArchiveJob
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

@Component
class ArchiveMetrics(
    val archiveJob: ReactiveArchiveJob,
    val archiveFileRepository: ArchiveFileRepository,
) : MeterBinder {
    lateinit var registry: MeterRegistry
    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        // 下载量，队列
        Gauge.builder(FILE_DOWNLOAD_ACTIVE_COUNT, archiveJob.httpDownloadPool) { it.activeCount.toDouble() }
            .description(FILE_DOWNLOAD_ACTIVE_COUNT_DESC)
            .register(registry)
        Gauge.builder(FILE_DOWNLOAD_QUEUE_SIZE, archiveJob.httpDownloadPool) { it.queue.size.toDouble() }
            .description(FILE_DOWNLOAD_QUEUE_SIZE_DESC)
            .register(registry)

        // 压缩量，队列
        Gauge.builder(FILE_COMPRESS_ACTIVE_COUNT, archiveJob.compressPool) { it.activeCount.toDouble() }
            .description(FILE_COMPRESS_ACTIVE_COUNT_DESC)
            .register(registry)
        Gauge.builder(FILE_COMPRESS_QUEUE_SIZE, archiveJob.compressPool) { it.queue.size.toDouble() }
            .description(FILE_COMPRESS_QUEUE_SIZE_DESC)
            .register(registry)

        // 上传量，队列
        Gauge.builder(FILE_UPLOAD_ACTIVE_COUNT, archiveJob.httpUploadPool) { it.activeCount.toDouble() }
            .description(FILE_UPLOAD_ACTIVE_COUNT_DESC)
            .register(registry)
        Gauge.builder(FILE_UPLOAD_QUEUE_SIZE, archiveJob.httpUploadPool) { it.queue.size.toDouble() }
            .description(FILE_UPLOAD_QUEUE_SIZE_DESC)
            .register(registry)

        // 归档文件状态
        ArchiveStatus.values().forEach {
            Gauge.builder(FILE_STATUS_COUNTER) { archiveFileRepository.countByStatus(it) }
                .description(FILE_RESTORED_COUNTER_DESC)
                .tag(TAG_STATUS, it.name)
                .register(registry)
        }
    }

    /**
     * 数量
     * */
    fun getCounter(action: Action, key: String): Counter {
        val c = if (action == Action.ARCHIVED) {
            Counter.builder(FILE_ARCHIVED_COUNTER)
                .description(FILE_ARCHIVED_COUNTER_DESC)
        } else {
            Counter.builder(FILE_RESTORED_COUNTER)
                .description(FILE_RESTORED_COUNTER_DESC)
        }
        return c.tag(TAG_CREDENTIALS_KEY, key)
            .register(registry)
    }

    /**
     * 大小
     * */
    fun getSizeCounter(action: Action, key: String): Counter {
        val c = if (action == Action.ARCHIVED) {
            Counter.builder(FILE_ARCHIVED_SIZE_COUNTER)
                .description(FILE_ARCHIVED_SIZE_COUNTER_DESC)
        } else {
            Counter.builder(FILE_RESTORED_SIZE_COUNTER)
                .description(FILE_RESTORED_SIZE_COUNTER_DESC)
        }
        return c.tag(TAG_CREDENTIALS_KEY, key)
            .register(registry)
    }

    /**
     * 计时器
     * */
    fun getTimer(action: Action, key: String): Timer {
        val c = if (action == Action.ARCHIVED) {
            Timer.builder(FILE_ARCHIVED_TIME)
                .description(FILE_ARCHIVED_TIME_DESC)
        } else {
            Timer.builder(FILE_RESTORED_TIME)
                .description(FILE_RESTORED_TIME_DESC)
        }
        return c.tag(TAG_CREDENTIALS_KEY, key).register(registry)
    }

    enum class Action {
        ARCHIVED,
        RESTORED,
    }

    companion object {
        private const val FILE_ARCHIVED_COUNTER = "file.archived.count"
        private const val FILE_ARCHIVED_COUNTER_DESC = "文件归档数量"
        private const val FILE_RESTORED_COUNTER = "file.restored.count"
        private const val FILE_RESTORED_COUNTER_DESC = "文件恢复数量"
        private const val FILE_ARCHIVED_SIZE_COUNTER = "file.archived.size.count"
        private const val FILE_ARCHIVED_SIZE_COUNTER_DESC = "文件归档大小"
        private const val FILE_RESTORED_SIZE_COUNTER = "file.restored.size.count"
        private const val FILE_RESTORED_SIZE_COUNTER_DESC = "文件恢复大小"
        private const val FILE_ARCHIVED_TIME = "file.archived.time"
        private const val FILE_ARCHIVED_TIME_DESC = "文件归档耗时"
        private const val FILE_RESTORED_TIME = "file.restored.time"
        private const val FILE_RESTORED_TIME_DESC = "文件恢复耗时"
        private const val FILE_DOWNLOAD_ACTIVE_COUNT = "file.download.active.count"
        private const val FILE_DOWNLOAD_ACTIVE_COUNT_DESC = "文件下载实时数量"
        private const val FILE_DOWNLOAD_QUEUE_SIZE = "file.download.queue.size"
        private const val FILE_DOWNLOAD_QUEUE_SIZE_DESC = "文件下载队列大小"
        private const val FILE_UPLOAD_ACTIVE_COUNT = "file.upload.active.count"
        private const val FILE_UPLOAD_ACTIVE_COUNT_DESC = "文件上传实时数量"
        private const val FILE_UPLOAD_QUEUE_SIZE = "file.upload.queue.size"
        private const val FILE_UPLOAD_QUEUE_SIZE_DESC = "文件上传队列大小"
        private const val FILE_COMPRESS_ACTIVE_COUNT = "file.compress.active.count"
        private const val FILE_COMPRESS_ACTIVE_COUNT_DESC = "文件压缩实时数量"
        private const val FILE_COMPRESS_QUEUE_SIZE = "file.compress.queue.size"
        private const val FILE_COMPRESS_QUEUE_SIZE_DESC = "文件压缩队列大小"
        private const val FILE_STATUS_COUNTER = "file.status.count"
        private const val FILE_STATUS_COUNTER_DESC = "文件数量统计"
        private const val TAG_CREDENTIALS_KEY = "credentialsKey"
        private const val TAG_STATUS = "status"
    }
}
