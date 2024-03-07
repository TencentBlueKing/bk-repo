package com.tencent.bkrepo.archive.job.archive

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.DEEP_ARCHIVE
import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.job.FileProvider
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

@Component
class ArchiveManager(
    private val archiveProperties: ArchiveProperties,
    private val fileProvider: FileProvider,
    private val archiveFileDao: ArchiveFileDao,
    private val archiveFileRepository: ArchiveFileRepository,
    private val storageService: StorageService,
) {

    var cosClient: CosClient = CosClient(archiveProperties.cos)
    private val tika = Tika()
    private val compressPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.compressThreads,
        ThreadFactoryBuilder().setNameFormat("archive-compress-%d").build(),
    )
    private val xzCompressor = XZUtils(archiveProperties.compress.xzMemoryLimit.toBytes(), compressPool)
    private val emptyCompressor = EmptyUtils()
    private val compressedPath: Path = Paths.get(archiveProperties.workDir, "compressed")
    private val uncompressedPath: Path = Paths.get(archiveProperties.workDir, "uncompressed")

    init {
        if (!Files.exists(compressedPath)) {
            Files.createDirectories(compressedPath)
        }
        if (!Files.exists(uncompressedPath)) {
            Files.createDirectories(uncompressedPath)
        }
    }

    fun archive(file: TArchiveFile) {
        try {
            archive0(file)
        } catch (e: Exception) {
            logger.error("Archive file [${file.sha256}] error", e)
        }
    }

    fun restore(file: TArchiveFile) {
        try {
            restore0(file)
        } catch (e: Exception) {
            logger.error("Restore file [${file.sha256}] error", e)
        }
    }

    private fun archive0(file: TArchiveFile) {
        /*
        * 1. 下载文件
        * 2. 根据文件类型判断，应该使用哪种压缩算法
        * 3. 压缩文件
        * 4. 归档文件
        * 5. 更新数据库
        * */
        with(file) {
            logger.info("Start archive file [$sha256].")
            val tryLock = archiveFileDao.optimisticLock(
                file,
                TArchiveFile::status.name,
                ArchiveStatus.CREATED.name,
                ArchiveStatus.ARCHIVING.name,
            )
            if (!tryLock) {
                logger.info("File[$sha256] already start archive.")
                return
            }
            val dir = compressedPath.resolve(sha256)
            Files.createDirectories(dir)
            val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
            val begin = System.nanoTime()
            fileProvider.get(sha256, Range.full(size), credentials).flatMap {
                val filePath = dir.resolve(sha256)
                Files.move(it.toPath(), filePath)
                if (archiveProperties.compress.enabledCompress) {
                    val type = tika.detect(it)
                    val utilsName = determineCompressionUtils(type)
                    val compressionUtils = chooseCompressionUtils(utilsName)
                    file.compression = compressionUtils.name()
                    val path = dir.resolve(getKey(sha256, compression))
                    compressionUtils.compress(filePath, path)
                } else {
                    Mono.just(filePath.toFile())
                }
            }.doOnSuccess {
                val key = getKey(sha256, compression)
                val size = it.length()
                val throughput = measureThroughput(size) { cosClient.putFileObject(key, it, DEEP_ARCHIVE) }
                logger.info("Success upload $sha256,$throughput")
                file.compressedSize = size
                file.status = ArchiveStatus.ARCHIVED
                logger.info("Success to archive file [$sha256] on $storageCredentialsKey.")
            }.doOnError {
                logger.error("Failed to archive file [$sha256].", it)
                file.status = ArchiveStatus.ARCHIVE_FAILED
            }.doFinally {
                dir.toFile().deleteRecursively()
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
                val took = System.nanoTime() - begin
                val throughput = Throughput(size, took)
                val event = FileArchivedEvent(sha256, storageCredentialsKey, throughput)
                SpringContextUtils.publishEvent(event)
                logger.info("Complete archive file [$sha256] on $storageCredentialsKey")
            }.onErrorResume {
                Mono.empty()
            }.subscribe()
        }
    }

    private fun restore0(file: TArchiveFile) {
        /*
        * 1. 检查文件是否恢复
        * 2. 下载归档文件
        * 3. 恢复源文件
        * 4. 存储源文件
        * 5. 更新数据库
        * */
        with(file) {
            logger.info("Start restore file [$sha256].")
            val tryLock = archiveFileDao.optimisticLock(
                file,
                TArchiveFile::status.name,
                ArchiveStatus.WAIT_TO_RESTORE.name,
                ArchiveStatus.RESTORING.name,
            )
            if (!tryLock) {
                logger.info("File[$sha256] already start restore.")
                return
            }
            val key = getKey(sha256, compression)
            val checkObjectExistRequest = CheckObjectExistRequest(key)
            val restored = cosClient.checkObjectRestore(checkObjectExistRequest)
            if (!restored) {
                logger.info("$key is not ready.")
                status = ArchiveStatus.WAIT_TO_RESTORE
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
                return
            }
            val dir = compressedPath.resolve(sha256)
            Files.createDirectories(dir)
            val begin = System.nanoTime()
            fileProvider.get(key, Range.full(compressedSize), archiveProperties.cos).flatMap {
                val archiveFilePath = dir.resolve(key)
                Files.move(it.toPath(), archiveFilePath)
                val path = dir.resolve(sha256)
                chooseCompressionUtils(compression).uncompress(archiveFilePath, path)
            }.doOnSuccess {
                val artifactFile = it.toArtifactFile(true)
                val receiveSha256 = artifactFile.getFileSha256()
                if (receiveSha256 != sha256) {
                    error("File[${key()}] broken,receive $receiveSha256.)")
                }
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                storageService.store(sha256, artifactFile, storageCredentials)
                status = ArchiveStatus.RESTORED
                logger.info("Success to restore file ${this.key()}.")
            }.doOnError {
                status = ArchiveStatus.RESTORE_FAILED
                logger.error("Restore file $sha256 error: ", it)
            }.doFinally {
                dir.toFile().deleteRecursively()
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
                val took = System.nanoTime() - begin
                val throughput = Throughput(size, took)
                val event = FileRestoredEvent(sha256, storageCredentialsKey, throughput)
                SpringContextUtils.publishEvent(event)
                logger.info("Complete restore file [$sha256] on $storageCredentialsKey")
            }.onErrorResume {
                Mono.empty()
            }.subscribe()
        }
    }

    private fun determineCompressionUtils(type: String): String {
        // todo 根据文件类型选择压缩算法
        if (!archiveProperties.compress.enabledCompress) {
            return EmptyUtils.NAME
        }
        return XZUtils.NAME
    }

    private fun chooseCompressionUtils(name: String): CompressionUtils {
        return when (name) {
            XZUtils.NAME -> xzCompressor
            else -> emptyCompressor
        }
    }

    private fun getKey(name: String, compression: String): String {
        return name.plus(chooseCompressionUtils(compression).getSuffix())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveManager::class.java)
    }
}
