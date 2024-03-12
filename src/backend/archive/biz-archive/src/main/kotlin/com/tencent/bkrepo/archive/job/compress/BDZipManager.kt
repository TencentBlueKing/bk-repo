package com.tencent.bkrepo.archive.job.compress

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.event.StorageFileCompressedEvent
import com.tencent.bkrepo.archive.event.StorageFileUncompressedEvent
import com.tencent.bkrepo.archive.job.FileStorageFileProvider
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.bksync.transfer.exception.TooLowerReuseRateException
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.Stack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * bd压缩管理器，负责文件压缩与解压
 * */
@Component
class BDZipManager(
    private val compressFileDao: CompressFileDao,
    private val archiveProperties: ArchiveProperties,
    private val fileReferenceClient: FileReferenceClient,
    private val compressFileRepository: CompressFileRepository,
    private val storageService: StorageService,
) {
    private val workDir = archiveProperties.workDir.toPath()
    val signThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.signThreads,
        ThreadFactoryBuilder().setNameFormat("bd-sign-%d").build(),
    )
    val fileDownloadThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.ioThreads,
        ThreadFactoryBuilder().setNameFormat("bd-io-%d").build(),
        PriorityBlockingQueue(),
    )
    val diffThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.diffThreads,
        ThreadFactoryBuilder().setNameFormat("bd-diff-%d").build(),
    )

    val bigCompressPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.bigFileCompressPoolSize,
        ThreadFactoryBuilder().setNameFormat("bd-bigfile-diff-%d").build(),
    )

    val patchThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.patchThreads,
        ThreadFactoryBuilder().setNameFormat("bd-patch-%d").build(),
    )

    val fileProvider = FileStorageFileProvider(
        workDir.resolve(DOWNLOAD_DIR),
        archiveProperties.compress.highWaterMark.toBytes(),
        archiveProperties.compress.lowWaterMark.toBytes(),
        fileDownloadThreadPool,
    )
    private val checksumProvider = ChecksumFileProvider(
        workDir.resolve(SIGN_DIR),
        fileProvider,
        archiveProperties.compress.signFileCacheTime,
        signThreadPool,
    )
    private val bdCompressor = BDCompressor(
        archiveProperties.compress.ratio,
        diffThreadPool,
        bigCompressPool,
        archiveProperties.compress.bigChecksumFileThreshold.toBytes(),
    )
    private val bdUncompressor = BDUncompressor(patchThreadPool)
    private val prioritySeq = AtomicInteger(Int.MIN_VALUE)
    val runningTasks = AtomicInteger()
    val taskQueue = ArrayBlockingQueue<TCompressFile>(DEFAULT_BUFFER_SIZE)

    init {
        val dirs = listOf(DOWNLOAD_DIR, SIGN_DIR, COMPRESS_DIR, UNCOMPRESS_DIR)
        dirs.forEach {
            val filePath = workDir.resolve(it)
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath)
            }
        }
        Flux.interval(archiveProperties.compress.fetchInterval)
            .publishOn(Schedulers.fromExecutor(Executors.newSingleThreadExecutor()))
            .doOnNext {
                /*
                * 在本机空闲时，从db获取新的数据。
                * */
                if (!serverIsBusy()) {
                    if (taskQueue.size == 0) {
                        fillTaskFromDb()
                    }
                    while (!serverIsBusy() && taskQueue.size > 0) {
                        doOnNextTask()
                    }
                }
            }.subscribe()
    }

    fun compress(file: TCompressFile) {
        try {
            if (serverIsBusy()) {
                taskQueue.offer(file)
            } else {
                compress0(file)
            }
        } catch (e: Exception) {
            logger.error("Compress file [${file.sha256}] error", e)
        }
    }

    fun uncompress(file: TCompressFile) {
        try {
            if (serverIsBusy()) {
                taskQueue.offer(file)
            } else {
                val fileStack = Stack<TCompressFile>()
                fileStack.push(file)
                var rootFile = compressFileRepository.findBySha256AndStorageCredentialsKeyAndStatusIn(
                    file.baseSha256,
                    file.storageCredentialsKey,
                    setOf(CompressStatus.COMPLETED, CompressStatus.COMPRESSED),
                )
                // 级联解压
                while (rootFile != null) {
                    rootFile.status = CompressStatus.WAIT_TO_UNCOMPRESS
                    compressFileRepository.save(rootFile)
                    fileStack.push(rootFile)
                    rootFile = compressFileRepository.findBySha256AndStorageCredentialsKeyAndStatusIn(
                        rootFile.baseSha256,
                        rootFile.storageCredentialsKey,
                        setOf(CompressStatus.COMPLETED, CompressStatus.COMPRESSED),
                    )
                }
                logger.info("Uncompress chain len ${fileStack.size}.")
                uncompress0(fileStack)
            }
        } catch (e: Exception) {
            logger.error("Uncompress file [${file.sha256}] error", e)
        }
    }

    fun serverIsBusy(): Boolean {
        val isBusyNow = !fileProvider.isActive() || runningTasks.get() > archiveProperties.compress.maxConcurrency
        if (isBusyNow) {
            logger.warn("Server is busy now. downloader: ${fileProvider.isActive()},running: $runningTasks")
        }
        return isBusyNow
    }

    private fun compress0(file: TCompressFile) {
        with(file) {
            logger.info("Start compress file [$sha256].")
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                file,
                TCompressFile::status.name,
                CompressStatus.CREATED.name,
                CompressStatus.COMPRESSING.name,
            )
            if (!tryLock) {
                logger.info("File[${file.sha256}] already start compress.")
                return
            }
            // 增量存储源文件和基础文件必须不同，不然会导致base文件丢失
            require(sha256 != baseSha256) { "Incremental storage source file and base file must be different." }
            // 压缩
            val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
            val workDir = Paths.get(workDir.toString(), COMPRESS_DIR, sha256)
            val srcFile = fileProvider.get(sha256, Range.full(uncompressedSize), credentials)
            val baseRange = if (baseSize == null) Range.FULL_RANGE else Range.full(baseSize)
            val checksumFile = checksumProvider.get(baseSha256, baseRange, credentials)
            val begin = System.nanoTime()
            runningTasks.incrementAndGet()
            bdCompressor.compress(srcFile, checksumFile, sha256, baseSha256, workDir)
                .doOnSuccess {
                    val newFileName = sha256.plus(BD_FILE_SUFFIX)
                    storageService.store(newFileName, it.toArtifactFile(), credentials)
                    file.compressedSize = it.length()
                    file.status = CompressStatus.COMPRESSED
                    logger.info("Success to compress file [$sha256] on $storageCredentialsKey.")
                }
                .doOnError {
                    if (it !is TooLowerReuseRateException) {
                        logger.error("Failed to compress file [$sha256].", it)
                    }
                    status = CompressStatus.COMPRESS_FAILED
                    fileReferenceClient.decrement(baseSha256, storageCredentialsKey)
                }
                .doFinally {
                    runningTasks.decrementAndGet()
                    workDir.toFile().deleteRecursively()
                    file.lastModifiedDate = LocalDateTime.now()
                    compressFileRepository.save(file)
                    // 发送压缩事件
                    val took = System.nanoTime() - begin
                    val throughput = Throughput(uncompressedSize, took)
                    val event = StorageFileCompressedEvent(
                        sha256 = sha256,
                        baseSha256 = baseSha256,
                        uncompressed = uncompressedSize,
                        compressed = compressedSize,
                        storageCredentialsKey = storageCredentialsKey,
                        throughput = throughput,
                    )
                    SpringContextUtils.publishEvent(event)
                    logger.info("Complete compress file [$sha256] on $storageCredentialsKey")
                    doOnNextTask()
                }
                .onErrorResume { Mono.empty() }
                .subscribe()
        }
    }

    private fun uncompress0(fileStack: Stack<TCompressFile>) {
        if (fileStack.empty()) {
            doOnNextTask()
            return
        }
        with(fileStack.pop()) {
            logger.info("Start uncompress file [$sha256].")
            val tryLock = compressFileDao.optimisticLock(
                this,
                TCompressFile::status.name,
                CompressStatus.WAIT_TO_UNCOMPRESS.name,
                CompressStatus.UNCOMPRESSING.name,
            )
            if (!tryLock) {
                logger.info("File[${this.sha256}] already start uncompress.")
                return
            }
            val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
            val workDir = Paths.get(workDir.toString(), UNCOMPRESS_DIR, sha256)
            val bdFileName = sha256.plus(BD_FILE_SUFFIX)
            val bdFile = fileProvider.get(
                bdFileName,
                Range.full(compressedSize),
                credentials,
                prioritySeq.getAndIncrement(),
            )
            val baseRange = if (baseSize == null) Range.FULL_RANGE else Range.full(baseSize)
            val baseFile =
                fileProvider.get(baseSha256, baseRange, credentials, prioritySeq.getAndIncrement())
            val begin = System.nanoTime()
            runningTasks.incrementAndGet()
            bdUncompressor.patch(bdFile, baseFile, sha256, workDir)
                .doOnSuccess {
                    // 更新状态
                    this.status = CompressStatus.UNCOMPRESSED
                    storageService.store(sha256, it.toArtifactFile(), credentials)
                    storageService.delete(bdFileName, credentials)
                    logger.info("Success to uncompress file [$sha256] on $storageCredentialsKey")
                    uncompress0(fileStack)
                }
                .doOnError {
                    logger.error("Failed to uncompress file [$sha256] on $storageCredentialsKey", it)
                    this.status = CompressStatus.UNCOMPRESS_FAILED
                }
                .doFinally {
                    runningTasks.decrementAndGet()
                    workDir.toFile().deleteRecursively()
                    this.lastModifiedDate = LocalDateTime.now()
                    compressFileRepository.save(this)
                    val took = System.nanoTime() - begin
                    val throughput = Throughput(uncompressedSize, took)
                    val event = StorageFileUncompressedEvent(
                        sha256 = sha256,
                        compressed = compressedSize,
                        uncompressed = uncompressedSize,
                        storageCredentialsKey = storageCredentialsKey,
                        throughput = throughput,
                    )
                    SpringContextUtils.publishEvent(event)
                    logger.info("Complete uncompress file [$sha256] on $storageCredentialsKey")
                }
                .onErrorResume { Mono.empty() }
                .subscribe()
        }
    }

    /**
     * 从db中按时间逆序获取任务，像工作窃取算法一样，从端尾获取元素。
     * */
    private fun fillTaskFromDb() {
        val wantPull = archiveProperties.compress.maxConcurrency - runningTasks.get()
        if (wantPull <= 0) {
            return
        }
        logger.info("Start fetch task from db.")
        val criteria = where(TCompressFile::status).inValues(
            CompressStatus.CREATED,
            CompressStatus.WAIT_TO_UNCOMPRESS,
        )
        val sort = Sort.by(Sort.Direction.DESC, TCompressFile::lastModifiedDate.name)
        val query = Query.query(criteria)
            .limit(wantPull)
            .with(sort)
        val files = compressFileDao.find(query)
        logger.info("Fetch ${files.size} tasks from db.")
        files.forEach { taskQueue.offer(it) }
    }

    private fun doOnNextTask() {
        val poll = taskQueue.poll() ?: return
        when (poll.status) {
            CompressStatus.CREATED -> compress(poll)
            CompressStatus.WAIT_TO_UNCOMPRESS -> uncompress(poll)
            else -> error("Unexpected file status ${poll.status}.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BDZipManager::class.java)
        private const val BD_FILE_SUFFIX = ".bd"
        private const val DOWNLOAD_DIR = "download"
        private const val SIGN_DIR = "sign"
        private const val COMPRESS_DIR = "compress"
        private const val UNCOMPRESS_DIR = "uncompress"
    }
}
