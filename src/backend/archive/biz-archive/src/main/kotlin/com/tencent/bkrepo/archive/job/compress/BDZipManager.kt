package com.tencent.bkrepo.archive.job.compress

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.event.StorageFileCompressedEvent
import com.tencent.bkrepo.archive.event.StorageFileUncompressedEvent
import com.tencent.bkrepo.archive.job.BufferedResourceManager
import com.tencent.bkrepo.archive.job.PriorityFileProvider
import com.tencent.bkrepo.archive.job.TaskResult
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
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoSink
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.Stack
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
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
    private val fileProvider: PriorityFileProvider,
) : BufferedResourceManager<TCompressFile>(
    LinkedBlockingQueue(archiveProperties.pendingQueueSize),
    archiveProperties.gc.maxConcurrency,
) {
    private val workDir = archiveProperties.gc.path.toPath()
    val signThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.gc.signThreads,
        ThreadFactoryBuilder().setNameFormat("bd-sign-%d").build(),
    )
    val fileDownloadThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.gc.ioThreads,
        ThreadFactoryBuilder().setNameFormat("bd-io-%d").build(),
        PriorityBlockingQueue(),
    )
    val diffThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.gc.diffThreads,
        ThreadFactoryBuilder().setNameFormat("bd-diff-%d").build(),
    )

    val bigCompressPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.gc.bigFileCompressPoolSize,
        ThreadFactoryBuilder().setNameFormat("bd-bigfile-diff-%d").build(),
    )

    val patchThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.gc.patchThreads,
        ThreadFactoryBuilder().setNameFormat("bd-patch-%d").build(),
    )

    private val checksumProvider = ChecksumFileProvider(
        workDir.resolve(SIGN_DIR),
        fileProvider,
        archiveProperties.gc.signFileCacheTime,
        signThreadPool,
    )
    private val bdCompressor = BDCompressor(
        archiveProperties.gc.ratio,
        diffThreadPool,
        bigCompressPool,
        archiveProperties.gc.bigChecksumFileThreshold.toBytes(),
    )
    private val bdUncompressor = BDUncompressor(patchThreadPool)
    private val prioritySeq = AtomicInteger(Int.MIN_VALUE)

    init {
        val dirs = listOf(DOWNLOAD_DIR, SIGN_DIR, COMPRESS_DIR, UNCOMPRESS_DIR)
        dirs.forEach {
            val filePath = workDir.resolve(it)
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath)
            }
        }
    }

    override fun process0(resource: TCompressFile): Mono<TaskResult> {
        return when (resource.status) {
            CompressStatus.CREATED -> compress(resource)
            CompressStatus.WAIT_TO_UNCOMPRESS -> uncompress(resource)
            else -> error("Not support")
        }
    }

    override fun enqueue(resource: TCompressFile): Boolean {
        if (resource.status == CompressStatus.WAIT_TO_UNCOMPRESS) {
            // 怎么保证解压的文件一定能入队列
            val deque = super.queue as LinkedBlockingDeque
            while (!deque.offerFirst(resource)) {
                if (deque.peekLast().status == CompressStatus.WAIT_TO_UNCOMPRESS) {
                    return false
                }
                deque.pollLast()
            }
            return true
        } else {
            return super.enqueue(resource)
        }
    }

    private fun compress(file: TCompressFile): Mono<TaskResult> {
        try {
            return compress0(file)
        } catch (e: Exception) {
            logger.error("Compress file [${file.sha256}] error", e)
        }
        return Mono.just(TaskResult.FAILED)
    }

    private fun uncompress(file: TCompressFile): Mono<TaskResult> {
        try {
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
            return Mono.create { uncompress0(fileStack, it) }
        } catch (e: Exception) {
            logger.error("Uncompress file [${file.sha256}] error", e)
        }
        return Mono.just(TaskResult.FAILED)
    }

    private fun compress0(file: TCompressFile): Mono<TaskResult> {
        logger.info("Start compress file [${file.sha256}].")
        // 乐观锁
        val tryLock = compressFileDao.optimisticLock(
            file,
            TCompressFile::status.name,
            CompressStatus.CREATED.name,
            CompressStatus.COMPRESSING.name,
        )
        if (!tryLock) {
            logger.info("File[${file.sha256}] already start compress.")
            return Mono.just(TaskResult.OK)
        }
        return Mono.create { sink ->
            with(file) {
                // 增量存储源文件和基础文件必须不同，不然会导致base文件丢失
                require(sha256 != baseSha256) { "Incremental storage source file and base file must be different." }
                // 压缩
                val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                val workDir = Paths.get(workDir.toString(), COMPRESS_DIR, sha256)
                val srcFile = fileProvider.get(sha256, Range.full(uncompressedSize), credentials)
                val baseRange = if (baseSize == null) Range.FULL_RANGE else Range.full(baseSize)
                val checksumFile = checksumProvider.get(baseSha256, baseRange, credentials)
                val begin = System.nanoTime()
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
                        sink.success(TaskResult.OK)
                    }
                    .onErrorResume { Mono.empty() }
                    .subscribe()
            }
        }
    }

    private fun uncompress0(fileStack: Stack<TCompressFile>, sink: MonoSink<TaskResult>) {
        if (fileStack.empty()) {
            sink.success(TaskResult.OK)
            return
        }
        val file = fileStack.pop()
        logger.info("Start uncompress file [${file.sha256}].")
        val tryLock = compressFileDao.optimisticLock(
            file,
            TCompressFile::status.name,
            CompressStatus.WAIT_TO_UNCOMPRESS.name,
            CompressStatus.UNCOMPRESSING.name,
        )
        if (!tryLock) {
            logger.info("File[${file.sha256}] already start uncompress.")
            sink.success(TaskResult.OK)
            return
        }
        with(file) {
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
            bdUncompressor.patch(bdFile, baseFile, sha256, workDir)
                .doOnSuccess {
                    // 更新状态
                    this.status = CompressStatus.UNCOMPRESSED
                    storageService.store(sha256, it.toArtifactFile(), credentials)
                    storageService.delete(bdFileName, credentials)
                    logger.info("Success to uncompress file [$sha256] on $storageCredentialsKey")
                }
                .doOnError {
                    logger.error("Failed to uncompress file [$sha256] on $storageCredentialsKey", it)
                    this.status = CompressStatus.UNCOMPRESS_FAILED
                }
                .doFinally {
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
                    if (status == CompressStatus.UNCOMPRESSED) {
                        uncompress0(fileStack, sink)
                    } else {
                        // 解压失败，解压事件结束
                        sink.success(TaskResult.OK)
                    }
                }
                .onErrorResume { Mono.empty() }
                .subscribe()
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
