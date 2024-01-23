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
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime

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
    )
    val diffThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.diffThreads,
        ThreadFactoryBuilder().setNameFormat("bd-diff-%d").build(),
    )
    val patchThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.patchThreads,
        ThreadFactoryBuilder().setNameFormat("bd-patch-%d").build(),
    )

    private val fileProvider = FileStorageFileProvider(
        workDir.resolve(DOWNLOAD_DIR),
        archiveProperties.threshold.toBytes(),
        fileDownloadThreadPool,
    )
    private val checksumProvider = ChecksumFileProvider(
        workDir.resolve(SIGN_DIR),
        fileProvider,
        archiveProperties.compress.signFileCacheTime,
        signThreadPool,
    )
    private val bdCompressor = BDCompressor(archiveProperties.compress.ratio, diffThreadPool)
    private val bdUncompressor = BDUncompressor(patchThreadPool)

    init {
        val dirs = listOf(DOWNLOAD_DIR, SIGN_DIR, COMPRESS_DIR, UNCOMPRESS_DIR)
        dirs.forEach {
            val filePath = workDir.resolve(it)
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath)
            }
        }
    }

    fun compress(file: TCompressFile) {
        try {
            compress0(file)
        } catch (e: Exception) {
            logger.error("Compress file [${file.sha256}] error", e)
        }
    }

    fun uncompress(file: TCompressFile) {
        try {
            uncompress0(file)
        } catch (e: Exception) {
            logger.error("Uncompress file [${file.sha256}] error", e)
        }
    }

    private fun compress0(file: TCompressFile) {
        with(file) {
            logger.info("Start compress file [$sha256].")
            // 增量存储源文件和基础文件必须不同，不然会导致base文件丢失
            require(sha256 != baseSha256) { "Incremental storage source file and base file must be different." }
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                file,
                TCompressFile::status.name,
                CompressStatus.CREATED.name,
                CompressStatus.COMPRESSING.name,
            )
            if (!tryLock) {
                logger.info("File[$sha256] already start compress.")
                return
            }
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
                    logger.info("Failed to compress file [$sha256].")
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
                }.subscribe()
        }
    }

    private fun uncompress0(file: TCompressFile) {
        with(file) {
            logger.info("Start uncompress file [$sha256].")
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                file,
                TCompressFile::status.name,
                CompressStatus.WAIT_TO_UNCOMPRESS.name,
                CompressStatus.UNCOMPRESSING.name,
            )
            if (!tryLock) {
                logger.info("File[$sha256] already start uncompress.")
                return
            }
            // 解压
            val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
            compressFileRepository.findBySha256AndStorageCredentialsKey(baseSha256, storageCredentialsKey)?.let {
                if (file.status == CompressStatus.COMPLETED || file.status == CompressStatus.COMPRESSED) {
                    uncompress(it)
                }
            }
            val workDir = Paths.get(workDir.toString(), UNCOMPRESS_DIR, sha256)
            val bdFileName = sha256.plus(BD_FILE_SUFFIX)
            val bdFile = fileProvider.get(bdFileName, Range.full(compressedSize), credentials)
            val baseRange = if (baseSize == null) Range.FULL_RANGE else Range.full(baseSize)
            val baseFile = fileProvider.get(baseSha256, baseRange, credentials)
            val begin = System.nanoTime()
            bdUncompressor.patch(bdFile, baseFile, sha256, workDir)
                .doOnSuccess {
                    // 更新状态
                    file.status = CompressStatus.UNCOMPRESSED
                    storageService.store(sha256, it.toArtifactFile(), credentials)
                    storageService.delete(bdFileName, credentials)
                    logger.info("Success to uncompress file [$sha256] on $storageCredentialsKey")
                }
                .doOnError {
                    file.status = CompressStatus.UNCOMPRESS_FAILED
                    logger.info("Failed to uncompress file [$sha256] on $storageCredentialsKey")
                }
                .doFinally {
                    workDir.toFile().deleteRecursively()
                    file.lastModifiedDate = LocalDateTime.now()
                    compressFileRepository.save(file)
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
