package com.tencent.bkrepo.archive.job.archive

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveUtils.Companion.newFixedAndCachedThreadPool
import com.tencent.bkrepo.archive.utils.ReactiveDaoUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 数据归档任务
 * */
@Component
class ArchiveJob(
    private val reactiveMongoTemplate: ReactiveMongoTemplate,
    private val archiveProperties: ArchiveProperties,
    private val storageService: StorageService,
    private val archiveFileRepository: ArchiveFileRepository,
) : Cancellable {

    /**
     * 归档cos实例
     * */
    private val cosClient = CosClient(archiveProperties.cos)

    /**
     * 工作路径
     * */
    private val workPath = archiveProperties.workDir

    /**
     * 下载器
     * */
    private val downloader = FileDownloader(cosClient, workPath, storageService, archiveProperties.threshold.toBytes())

    /**
     * 压缩器
     * */
    private val compressor = FileCompressor(archiveProperties.xzMemoryLimit.toBytes(), workPath)

    /**
     * 上传器
     * */
    private val uploader = FileUploader(cosClient)

    /**
     * 下载任务线程池
     * */
    val httpDownloadPool = newFixedAndCachedThreadPool(
        archiveProperties.ioThreads,
        ThreadFactoryBuilder().setNameFormat("archive-download-%d").build(),
    )

    /**
     * 压缩任务线程池
     * */
    val compressPool = newFixedAndCachedThreadPool(
        archiveProperties.compressThreads,
        ThreadFactoryBuilder().setNameFormat("archive-compress-%d").build(),
    )

    /**
     * 上传任务线程池
     * */
    val httpUploadPool = newFixedAndCachedThreadPool(
        archiveProperties.ioThreads,
        ThreadFactoryBuilder().setNameFormat("archive-upload-%d").build(),
    )

    private var subscriber: ArchiveSubscriber? = null

    init {
        /*
         * 磁盘容量监控
         * */
        Flux.interval(Duration.ofMillis(DISK_CHECK_PERIOD))
            .map {
                val diskFreeInBytes = Paths.get(workPath).toFile().usableSpace
                diskFreeInBytes > archiveProperties.threshold.toBytes()
            }
            .subscribe {
                if (it) {
                    downloader.healthy()
                } else {
                    downloader.unHealthy()
                }
            }
    }

    /**
     * 获取待归档文件列表
     * */
    fun listFiles(): Flux<TArchiveFile> {
        val criteria = where(TArchiveFile::status).isEqualTo(ArchiveStatus.CREATED)
        val query = Query.query(criteria)
            .limit(archiveProperties.queryLimit)
        return ReactiveDaoUtils.query(query, TArchiveFile::class.java)
    }

    /**
     * 文件准备工作，使用乐观锁，保证文件只会进行一次归档。
     * */
    private fun prepare(archiveFile: TArchiveFile): Mono<ArchiveFileWrapper> {
        val criteria = Criteria.where(ID).isEqualTo(ObjectId(archiveFile.id))
            .and(TArchiveFile::status.name).isEqualTo(ArchiveStatus.CREATED.name)
        val now = LocalDateTime.now()
        val update = Update().set(TArchiveFile::status.name, ArchiveStatus.ARCHIVING.name)
            .set(TArchiveFile::lastModifiedDate.name, now)
        return reactiveMongoTemplate.updateFirst(Query.query(criteria), update, TArchiveFile::class.java)
            .flatMap {
                if (it.modifiedCount != 1L) {
                    logger.info("${archiveFile.key()} already start archive.")
                    Mono.empty()
                } else {
                    Mono.just(
                        ArchiveFileWrapper(archiveFile = archiveFile),
                    )
                }
            }
    }

    /**
     * 数据归档
     * */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    fun archive() {
        val subscriber = ArchiveSubscriber(archiveFileRepository)
        val prefetch = 1 // 防止多级缓存，导致上游flux读放大
        listFiles().flatMap(this::prepare, prefetch)
            .parallel()
            .runOn(Schedulers.fromExecutor(httpDownloadPool), prefetch)
            .flatMap(downloader::onArchiveFileWrapper, false, prefetch) // 下载
            .runOn(Schedulers.fromExecutor(compressPool), prefetch)
            .flatMap(compressor::onArchiveFileWrapper) // 压缩
            .runOn(Schedulers.fromExecutor(httpUploadPool), prefetch)
            .flatMap(uploader::onArchiveFileWrapper) // 上传
            .subscribe(subscriber)
        this.subscriber = subscriber
        subscriber.blockLast()
        this.subscriber = null
    }

    override fun cancel() {
        subscriber?.dispose()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveJob::class.java)
        private const val DISK_CHECK_PERIOD = 3000L
    }
}
