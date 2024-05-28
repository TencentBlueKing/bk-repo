package com.tencent.bkrepo.archive.metrics

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.core.provider.FileStorageFileProvider
import com.tencent.bkrepo.archive.core.provider.PriorityFileProvider
import com.tencent.bkrepo.archive.core.archive.ArchiveManager
import com.tencent.bkrepo.archive.core.compress.BDZipManager
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 归档度量指标
 * */
@Component
class ArchiveMetrics(
    val archiveFileRepository: ArchiveFileRepository,
    val compressFileRepository: CompressFileRepository,
    val bdZipManager: BDZipManager,
    val fileProvider: PriorityFileProvider,
    val archiveManager: ArchiveManager,
    private val archiveFileDao: ArchiveFileDao,
    private val compressFileDao: CompressFileDao,
) : MeterBinder {
    lateinit var registry: MeterRegistry

    private var fileArchiveSizeTotal = 0L
    private var fileCompressSizeTotal = 0L

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        // 下载量，队列
        val fsfp = fileProvider as FileStorageFileProvider
        Gauge.builder(FILE_DOWNLOAD_ACTIVE_COUNT, fsfp.executor) { it.activeCount.toDouble() }
            .description(FILE_DOWNLOAD_ACTIVE_COUNT_DESC)
            .register(registry)
        Gauge.builder(FILE_DOWNLOAD_QUEUE_SIZE, fsfp.executor) { it.queue.size.toDouble() }
            .description(FILE_DOWNLOAD_QUEUE_SIZE_DESC)
            .register(registry)

        // gc量，队列
        val workThreadPool2 = bdZipManager.bigFileWorkThreadPool
        Gauge.builder(FILE_COMPRESS_ACTIVE_COUNT, bdZipManager.workThreadPool) {
            it.activeCount.toDouble() + workThreadPool2.activeCount.toDouble()
        }.description(FILE_COMPRESS_ACTIVE_COUNT_DESC).register(registry)
        Gauge.builder(FILE_COMPRESS_QUEUE_SIZE, bdZipManager.workThreadPool) {
            it.queue.size.toDouble() + workThreadPool2.queue.size.toDouble()
        }.description(FILE_COMPRESS_QUEUE_SIZE_DESC).register(registry)

        // 归档量,队列
        Gauge.builder(FILE_ARCHIVE_ACTIVE_COUNTER, archiveManager.archiveThreadPool) { it.activeCount.toDouble() }
            .description(FILE_ARCHIVE_ACTIVE_COUNTER_DESC)
            .register(registry)
        Gauge.builder(FILE_ARCHIVE_QUEUE_SIZE, archiveManager.archiveThreadPool) { it.queue.size.toDouble() }
            .description(FILE_ARCHIVE_QUEUE_SIZE_DESC)
            .register(registry)

        // 归档文件状态
        ArchiveStatus.values().forEach {
            Gauge.builder(ARCHIVE_FILE_STATUS_COUNTER) { archiveFileRepository.countByStatus(it) }
                .description(ARCHIVE_FILE_STATUS_COUNTER_DESC)
                .tag(TAG_STATUS, it.name)
                .register(registry)
        }
        Gauge.builder(FILE_ARCHIVE_SIZE_TOTAL_COUNTER) { fileArchiveSizeTotal }
            .description(FILE_ARCHIVE_SIZE_TOTAL_COUNTER_DESC)
            .register(registry)

        // 压缩文件状态
        CompressStatus.values().forEach {
            Gauge.builder(COMPRESS_FILE_STATUS_COUNTER) { compressFileRepository.countByStatus(it) }
                .description(COMPRESS_FILE_STATUS_COUNTER_DESC)
                .tag(TAG_STATUS, it.name)
                .register(registry)
        }
        Gauge.builder(FILE_COMPRESS_SIZE_TOTAL_COUNTER) { fileCompressSizeTotal }
            .description(FILE_COMPRESS_SIZE_TOTAL_COUNTER_DESC)
            .register(registry)
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.DAYS)
    fun updateArchiveAndCompressSizeTotal() {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(where(TArchiveFile::status).isEqualTo(ArchiveStatus.COMPLETED)),
            Aggregation.group().sum(TArchiveFile::size.name).`as`(SizeInfo::size.name),
        )
        val aggregateResult = archiveFileDao.aggregate(aggregation, HashMap::class.java)
        fileArchiveSizeTotal = aggregateResult.mappedResults.firstOrNull()?.get(SizeInfo::size.name) as? Long ?: 0
        val aggregation2 = Aggregation.newAggregation(
            Aggregation.match(where(TCompressFile::status).isEqualTo(CompressStatus.COMPLETED)),
            Aggregation.group().sum(TCompressFile::uncompressedSize.name).`as`(SizeInfo::size.name),
        )
        val aggregateResult2 = compressFileDao.aggregate(aggregation2, HashMap::class.java)
        fileCompressSizeTotal = aggregateResult2.mappedResults.firstOrNull()?.get(SizeInfo::size.name) as? Long ?: 0
    }

    /**
     * 获取数量计数器
     * @param action 当前操作
     * @param credentialsKey 存储key
     * */
    fun getCounter(action: Action, credentialsKey: String): Counter {
        val builder = when (action) {
            Action.ARCHIVED -> Counter.builder(FILE_ARCHIVE_COUNTER)
                .description(FILE_ARCHIVE_COUNTER_DESC)

            Action.RESTORED -> Counter.builder(FILE_RESTORE_COUNTER)
                .description(FILE_RESTORE_COUNTER_DESC)

            Action.COMPRESSED -> Counter.builder(FILE_COMPRESS_COUNTER)
                .description(FILE_COMPRESS_COUNTER_DESC)

            Action.UNCOMPRESSED -> Counter.builder(FILE_UNCOMPRESS_COUNTER)
                .description(FILE_UNCOMPRESS_COUNTER_DESC)

            Action.STORAGE_FREE -> Counter.builder(STORAGE_FREE_COUNTER)
                .description(STORAGE_FREE_COUNTER_DESC)

            Action.STORAGE_ALLOCATE -> Counter.builder(STORAGE_ALLOCATE_COUNTER)
                .description(STORAGE_ALLOCATE_COUNTER_DESC)
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
            Action.ARCHIVED -> Counter.builder(FILE_ARCHIVE_SIZE_COUNTER)
                .description(FILE_ARCHIVE_SIZE_COUNTER_DESC)

            Action.RESTORED -> Counter.builder(FILE_RESTORE_SIZE_COUNTER)
                .description(FILE_RESTORE_SIZE_COUNTER_DESC)

            Action.COMPRESSED -> Counter.builder(FILE_COMPRESS_SIZE_COUNTER)
                .description(FILE_COMPRESS_SIZE_COUNTER_DESC)

            Action.UNCOMPRESSED -> Counter.builder(FILE_UNCOMPRESS_SIZE_COUNTER)
                .description(FILE_UNCOMPRESS_SIZE_COUNTER_DESC)

            Action.STORAGE_FREE -> Counter.builder(STORAGE_FREE_SIZE_COUNTER)
                .description(STORAGE_FREE_SIZE_COUNTER_DESC)

            Action.STORAGE_ALLOCATE -> Counter.builder(STORAGE_ALLOCATE_SIZE_COUNTER)
                .description(STORAGE_ALLOCATE_SIZE_COUNTER_DESC)
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
            Action.ARCHIVED -> Timer.builder(FILE_ARCHIVE_TIME)
                .description(FILE_ARCHIVE_TIME_DESC)

            Action.RESTORED -> Timer.builder(FILE_RESTORE_TIME)
                .description(FILE_RESTORE_TIME_DESC)

            Action.COMPRESSED -> Timer.builder(FILE_COMPRESS_TIME)
                .description(FILE_COMPRESS_TIME_DESC)

            Action.UNCOMPRESSED -> Timer.builder(FILE_UNCOMPRESS_TIME)
                .description(FILE_UNCOMPRESS_TIME_DESC)

            else -> throw IllegalArgumentException("Action $action not support.")
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
        STORAGE_FREE,
        STORAGE_ALLOCATE,
    }

    data class SizeInfo(
        var size: Long,
    )

    companion object {
        private const val FILE_ARCHIVE_COUNTER = "file.archive.count"
        private const val FILE_ARCHIVE_COUNTER_DESC = "文件归档数量"
        private const val FILE_RESTORE_COUNTER = "file.restore.count"
        private const val FILE_RESTORE_COUNTER_DESC = "文件恢复数量"
        private const val FILE_ARCHIVE_SIZE_COUNTER = "file.archive.size.count"
        private const val FILE_ARCHIVE_SIZE_COUNTER_DESC = "文件归档大小"
        private const val FILE_RESTORE_SIZE_COUNTER = "file.restore.size.count"
        private const val FILE_RESTORE_SIZE_COUNTER_DESC = "文件恢复大小"
        private const val FILE_ARCHIVE_ACTIVE_COUNTER = "file.archive.active.count"
        private const val FILE_ARCHIVE_ACTIVE_COUNTER_DESC = "文件实时归档数量"
        private const val FILE_ARCHIVE_QUEUE_SIZE = "file.archive.queue.count"
        private const val FILE_ARCHIVE_QUEUE_SIZE_DESC = "文件归档队列大小"
        private const val FILE_ARCHIVE_TIME = "file.archive.time"
        private const val FILE_ARCHIVE_TIME_DESC = "文件归档耗时"
        private const val FILE_RESTORE_TIME = "file.restore.time"
        private const val FILE_RESTORE_TIME_DESC = "文件恢复耗时"
        private const val FILE_DOWNLOAD_ACTIVE_COUNT = "file.download.active.count"
        private const val FILE_DOWNLOAD_ACTIVE_COUNT_DESC = "文件下载实时数量"
        private const val FILE_DOWNLOAD_QUEUE_SIZE = "file.download.queue.size"
        private const val FILE_DOWNLOAD_QUEUE_SIZE_DESC = "文件下载队列大小"
        private const val FILE_COMPRESS_ACTIVE_COUNT = "file.compress.active.count"
        private const val FILE_COMPRESS_ACTIVE_COUNT_DESC = "文件压缩实时数量"
        private const val FILE_COMPRESS_QUEUE_SIZE = "file.compress.queue.size"
        private const val FILE_COMPRESS_QUEUE_SIZE_DESC = "文件压缩队列大小"
        private const val ARCHIVE_FILE_STATUS_COUNTER = "file.archive.status.count"
        private const val ARCHIVE_FILE_STATUS_COUNTER_DESC = "归档文件状态统计"
        private const val COMPRESS_FILE_STATUS_COUNTER = "file.compress.status.count"
        private const val COMPRESS_FILE_STATUS_COUNTER_DESC = "压缩文件状态统计"
        private const val FILE_COMPRESS_COUNTER = "file.compress.count"
        private const val FILE_COMPRESS_COUNTER_DESC = "文件压缩数量"
        private const val FILE_COMPRESS_SIZE_COUNTER = "file.compress.size.count"
        private const val FILE_COMPRESS_SIZE_COUNTER_DESC = "文件压缩大小"
        private const val FILE_COMPRESS_TIME = "file.compress.time"
        private const val FILE_COMPRESS_TIME_DESC = "文件压缩耗时"
        private const val FILE_UNCOMPRESS_COUNTER = "file.uncompress.count"
        private const val FILE_UNCOMPRESS_COUNTER_DESC = "文件压缩数量"
        private const val FILE_UNCOMPRESS_SIZE_COUNTER = "file.uncompress.size.count"
        private const val FILE_UNCOMPRESS_SIZE_COUNTER_DESC = "文件解压大小"
        private const val FILE_UNCOMPRESS_TIME = "file.uncompress.time"
        private const val FILE_UNCOMPRESS_TIME_DESC = "文件解压耗时"
        private const val STORAGE_FREE_SIZE_COUNTER = "storage.free.size.count"
        private const val STORAGE_FREE_SIZE_COUNTER_DESC = "存储释放大小"
        private const val STORAGE_FREE_COUNTER = "storage.free.count"
        private const val STORAGE_FREE_COUNTER_DESC = "存储释放文件个数"
        private const val STORAGE_ALLOCATE_SIZE_COUNTER = "storage.allocate.size.count"
        private const val STORAGE_ALLOCATE_SIZE_COUNTER_DESC = "存储新增大小"
        private const val STORAGE_ALLOCATE_COUNTER = "storage.allocate.count"
        private const val STORAGE_ALLOCATE_COUNTER_DESC = "存储新增文件个数"
        private const val FILE_ARCHIVE_SIZE_TOTAL_COUNTER = "file.archive.size.total.count"
        private const val FILE_ARCHIVE_SIZE_TOTAL_COUNTER_DESC = "文件归档总大小"
        private const val FILE_COMPRESS_SIZE_TOTAL_COUNTER = "file.compress.size.total.count"
        private const val FILE_COMPRESS_SIZE_TOTAL_COUNTER_DESC = "文件压缩总大小"
        private const val TAG_CREDENTIALS_KEY = "credentialsKey"
        private const val TAG_STATUS = "status"
    }
}
