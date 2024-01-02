package com.tencent.bkrepo.archive.metrics

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.job.archive.ArchiveJob
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * 归档度量指标
 * */
@Component
class ArchiveMetrics(
    val archiveJob: ArchiveJob,
    val archiveFileRepository: ArchiveFileRepository,
    val compressFileRepository: CompressFileRepository,
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
            Gauge.builder(ARCHIVE_FILE_STATUS_COUNTER) { archiveFileRepository.countByStatus(it) }
                .description(ARCHIVE_FILE_STATUS_COUNTER_DESC)
                .tag(TAG_STATUS, it.name)
                .register(registry)
        }

        // 压缩文件状态
        CompressStatus.values().forEach {
            Gauge.builder(COMPRESS_FILE_STATUS_COUNTER) { compressFileRepository.countByStatus(it) }
                .description(COMPRESS_FILE_STATUS_COUNTER_DESC)
                .tag(TAG_STATUS, it.name)
                .register(registry)
        }
    }

    /**
     * 获取数量计数器
     * @param action 当前操作
     * @param credentialsKey 存储key
     * */
    fun getCounter(action: Action, credentialsKey: String): Counter {
        val builder = when (action) {
            Action.ARCHIVED -> Counter.builder(FILE_ARCHIVED_COUNTER)
                .description(FILE_ARCHIVED_COUNTER_DESC)

            Action.RESTORED -> Counter.builder(FILE_RESTORED_COUNTER)
                .description(FILE_RESTORED_COUNTER_DESC)

            Action.COMPRESSED -> Counter.builder(FILE_COMPRESSED_COUNTER)
                .description(FILE_COMPRESSED_COUNTER_DESC)

            Action.UNCOMPRESSED -> Counter.builder(FILE_UNCOMPRESSED_COUNTER)
                .description(FILE_UNCOMPRESSED_COUNTER_DESC)
        }
        return builder.tag(TAG_CREDENTIALS_KEY, credentialsKey)
            .register(registry)
    }

    /**
     * 获取大小计数器
     * @param action 当前操作
     * @param credentialsKey 存储key
     * @param tags 额外tag
     * */
    fun getSizeCounter(action: Action, credentialsKey: String, vararg tags: String): Counter {
        val builder = when (action) {
            Action.ARCHIVED -> Counter.builder(FILE_ARCHIVED_SIZE_COUNTER)
                .description(FILE_ARCHIVED_SIZE_COUNTER_DESC)

            Action.RESTORED -> Counter.builder(FILE_RESTORED_SIZE_COUNTER)
                .description(FILE_RESTORED_SIZE_COUNTER_DESC)

            Action.COMPRESSED -> Counter.builder(FILE_COMPRESSED_SIZE_COUNTER)
                .description(FILE_COMPRESSED_SIZE_COUNTER_DESC)

            Action.UNCOMPRESSED -> Counter.builder(FILE_UNCOMPRESSED_SIZE_COUNTER)
                .description(FILE_UNCOMPRESSED_SIZE_COUNTER_DESC)
        }
        require(tags.size % 2 == 0)
        for (i in 0 until tags.lastIndex) {
            val key = tags[i]
            val value = tags[i + 1]
            builder.tag(key, value)
        }
        return builder.tag(TAG_CREDENTIALS_KEY, credentialsKey)
            .register(registry)
    }

    /**
     * 获取计时器
     * @param action 当前操作
     * @param credentialsKey 存储key
     * */
    fun getTimer(action: Action, credentialsKey: String): Timer {
        val builder = when (action) {
            Action.ARCHIVED -> Timer.builder(FILE_ARCHIVED_TIME)
                .description(FILE_ARCHIVED_TIME_DESC)

            Action.RESTORED -> Timer.builder(FILE_RESTORED_TIME)
                .description(FILE_RESTORED_TIME_DESC)

            Action.COMPRESSED -> Timer.builder(FILE_COMPRESSED_TIME)
                .description(FILE_COMPRESSED_TIME_DESC)

            Action.UNCOMPRESSED -> Timer.builder(FILE_UNCOMPRESSED_TIME)
                .description(FILE_UNCOMPRESSED_TIME_DESC)
        }
        return builder.tag(TAG_CREDENTIALS_KEY, credentialsKey)
            .register(registry)
    }

    /**
     * 归档服务相关动作
     * */
    enum class Action {
        ARCHIVED,
        RESTORED,
        COMPRESSED,
        UNCOMPRESSED,
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
        private const val ARCHIVE_FILE_STATUS_COUNTER = "file.archive.status.count"
        private const val ARCHIVE_FILE_STATUS_COUNTER_DESC = "归档文件状态统计"
        private const val COMPRESS_FILE_STATUS_COUNTER = "file.compress.status.count"
        private const val COMPRESS_FILE_STATUS_COUNTER_DESC = "压缩文件状态统计"
        private const val FILE_COMPRESSED_COUNTER = "file.compress.count"
        private const val FILE_COMPRESSED_COUNTER_DESC = "文件压缩数量"
        private const val FILE_COMPRESSED_SIZE_COUNTER = "file.compress.size.count"
        private const val FILE_COMPRESSED_SIZE_COUNTER_DESC = "文件压缩大小"
        private const val FILE_COMPRESSED_TIME = "file.compress.time"
        private const val FILE_COMPRESSED_TIME_DESC = "文件压缩耗时"
        private const val FILE_UNCOMPRESSED_COUNTER = "file.uncompress.count"
        private const val FILE_UNCOMPRESSED_COUNTER_DESC = "文件压缩数量"
        private const val FILE_UNCOMPRESSED_SIZE_COUNTER = "file.uncompress.size.count"
        private const val FILE_UNCOMPRESSED_SIZE_COUNTER_DESC = "文件解压大小"
        private const val FILE_UNCOMPRESSED_TIME = "file.uncompress.time"
        private const val FILE_UNCOMPRESSED_TIME_DESC = "文件解压耗时"
        private const val TAG_CREDENTIALS_KEY = "credentialsKey"
        private const val TAG_STATUS = "status"
    }
}
