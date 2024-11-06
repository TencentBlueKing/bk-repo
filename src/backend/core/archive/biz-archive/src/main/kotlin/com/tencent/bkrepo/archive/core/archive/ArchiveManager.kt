package com.tencent.bkrepo.archive.core.archive

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.core.provider.FileTask
import com.tencent.bkrepo.archive.event.FileArchivedEvent
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.core.provider.PriorityFileProvider
import com.tencent.bkrepo.archive.core.TaskResult
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

@Component
class ArchiveManager(
    private val archiveProperties: ArchiveProperties,
    private val fileProvider: PriorityFileProvider,
) : Function<TArchiveFile, Mono<TaskResult>> {
    @Autowired
    @Lazy
    private lateinit var archiveFileDao: ArchiveFileDao

    @Autowired
    @Lazy
    private lateinit var archiveFileRepository: ArchiveFileRepository

    @Autowired
    @Lazy
    private lateinit var storageService: StorageService

    private val tika = Tika()
    private val compressPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.compressThreads,
        ThreadFactoryBuilder().setNameFormat("archive-compress-%d").build(),
    )
    private val xzArchiver = XZArchiver(archiveProperties.compress.xzMemoryLimit.toBytes(), compressPool)
    private val emptyArchiver = EmptyArchiver()
    private val compressedPath: Path = Paths.get(archiveProperties.workDir, "compressed")
    private val uncompressedPath: Path = Paths.get(archiveProperties.workDir, "uncompressed")
    val archiveThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.compress.compressThreads,
        ThreadFactoryBuilder().setNameFormat("archive-worker-%d").build(),
    )
    private val scheduler = Schedulers.fromExecutor(archiveThreadPool)
    private val prioritySeq = AtomicInteger(Int.MIN_VALUE)

    init {
        if (!Files.exists(compressedPath)) {
            Files.createDirectories(compressedPath)
        }
        if (!Files.exists(uncompressedPath)) {
            Files.createDirectories(uncompressedPath)
        }
    }

    override fun apply(t: TArchiveFile): Mono<TaskResult> {
        return when (t.status) {
            ArchiveStatus.CREATED -> archive(t)
            ArchiveStatus.WAIT_TO_RESTORE -> restore(t)
            else -> error("Not support")
        }
    }

    private fun archive(file: TArchiveFile): Mono<TaskResult> {
        try {
            return archive0(file)
        } catch (e: Exception) {
            logger.error("Archive file [${file.sha256}] error", e)
        }
        return Mono.just(TaskResult.FAILED)
    }

    private fun restore(file: TArchiveFile): Mono<TaskResult> {
        try {
            return restore0(file)
        } catch (e: Exception) {
            logger.error("Restore file [${file.sha256}] error", e)
        }
        return Mono.just(TaskResult.FAILED)
    }

    private fun archive0(file: TArchiveFile): Mono<TaskResult> {
        logger.info("Start archive file [${file.sha256}].")
        val tryLock = archiveFileDao.optimisticLock(
            file,
            TArchiveFile::status.name,
            ArchiveStatus.CREATED.name,
            ArchiveStatus.ARCHIVING.name,
        )
        if (!tryLock) {
            logger.info("File[${file.sha256}] already start archive.")
            return Mono.just(TaskResult.OK)
        }
        /*
           * 1. 下载文件
           * 2. 根据文件类型判断，应该使用哪种压缩算法
           * 3. 压缩文件
           * 4. 归档文件
           * 5. 更新数据库
           * */
        val sha256 = file.sha256
        val storageCredentialsKey = file.storageCredentialsKey
        val dir = compressedPath.resolve(sha256)
        Files.createDirectories(dir)
        val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
        val begin = System.nanoTime()
        val fileTask = FileTask(sha256, Range.full(file.size), credentials)
        val ret = fileProvider.get(fileTask)
            .publishOn(scheduler)
            .flatMap {
                val filePath = dir.resolve(sha256)
                Files.move(it.toPath(), filePath)
                if (archiveProperties.compress.enabledCompress) {
                    val type = tika.detect(it)
                    val archiverName = determineArchiverName(type)
                    val archiver = chooseArchiver(archiverName)
                    file.archiver = archiver.name()
                    val path = dir.resolve(getKey(sha256, file.archiver))
                    archiver.compress(filePath, path)
                } else {
                    Mono.just(filePath.toFile())
                }
            }.doOnSuccess {
                val key = getKey(sha256, file.archiver)
                val size = it.length()
                val throughput = measureThroughput(size) {
                    val storageCredentials = getStorageCredentials(file.archiveCredentialsKey)
                    val storageClass = file.storageClass ?: ArchiveStorageClass.DEEP_ARCHIVE
                    storageService.store(key, it.toArtifactFile(), storageCredentials, storageClass = storageClass.name)
                }
                logger.info("Success upload $key,$throughput")
                file.compressedSize = size
                file.status = ArchiveStatus.ARCHIVED
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
                logger.info("Success to archive file [$sha256] on $storageCredentialsKey.")
            }.doOnError {
                logger.error("Failed to archive file [$sha256].", it)
                file.status = ArchiveStatus.ARCHIVE_FAILED
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
            }.onErrorResume {
                Mono.empty()
            }.doFinally {
                dir.toFile().deleteRecursively()
                val took = System.nanoTime() - begin
                val throughput = Throughput(file.size, took)
                val event = FileArchivedEvent(sha256, storageCredentialsKey, throughput)
                SpringContextUtils.publishEvent(event)
                logger.info("Complete archive file [$sha256] on $storageCredentialsKey")
            }
        return ret.thenReturn(TaskResult.OK)
    }

    private fun restore0(file: TArchiveFile): Mono<TaskResult> {
        logger.info("Start restore file [${file.sha256}].")
        val tryLock = archiveFileDao.optimisticLock(
            file,
            TArchiveFile::status.name,
            ArchiveStatus.WAIT_TO_RESTORE.name,
            ArchiveStatus.RESTORING.name,
        )
        if (!tryLock) {
            logger.info("File[${file.sha256}] already start restore.")
            return Mono.just(TaskResult.OK)
        }
        /*
         * 1. 检查文件是否恢复
         * 2. 下载归档文件
         * 3. 恢复源文件
         * 4. 存储源文件
         * 5. 更新数据库
         * */
        val key = getKey(file.sha256, file.archiver)
        val credentials = getStorageCredentials(file.archiveCredentialsKey)
        val restored = storageService.checkRestore(key, credentials)
        if (!restored) {
            logger.info("$key is not ready.")
            file.status = ArchiveStatus.WAIT_TO_RESTORE
            file.lastModifiedDate = LocalDateTime.now()
            archiveFileRepository.save(file)
            return Mono.just(TaskResult.OK)
        }
        val sha256 = file.sha256
        val storageCredentialsKey = file.storageCredentialsKey
        val dir = uncompressedPath.resolve(sha256)
        Files.createDirectories(dir)
        val begin = System.nanoTime()
        val range = if (file.compressedSize == -1L) Range.FULL_RANGE else Range.full(file.compressedSize)
        val fileTask = FileTask(key, range, credentials, prioritySeq.getAndIncrement())
        val ret = fileProvider.get(fileTask)
            .publishOn(scheduler)
            .flatMap {
                val archiveFilePath = dir.resolve(key)
                Files.move(it.toPath(), archiveFilePath)
                val path = dir.resolve(sha256)
                chooseArchiver(file.archiver).uncompress(archiveFilePath, path)
            }.doOnSuccess {
                val artifactFile = it.toArtifactFile(true)
                val receiveSha256 = artifactFile.getFileSha256()
                if (receiveSha256 != sha256) {
                    error("File[$sha256] broken,receive $receiveSha256.)")
                }
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                storageService.store(sha256, artifactFile, storageCredentials)
                file.status = ArchiveStatus.RESTORED
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
                logger.info("Success to restore file [$sha256] on $storageCredentialsKey.")
            }.doOnError {
                file.status = ArchiveStatus.RESTORE_FAILED
                file.lastModifiedDate = LocalDateTime.now()
                archiveFileRepository.save(file)
                logger.error("Restore file $sha256 error: ", it)
            }.onErrorResume {
                Mono.empty()
            }.doFinally {
                dir.toFile().deleteRecursively()
                val took = System.nanoTime() - begin
                val throughput = Throughput(file.size, took)
                val event = FileRestoredEvent(sha256, storageCredentialsKey, throughput)
                SpringContextUtils.publishEvent(event)
                logger.info("Complete restore file [$sha256] on $storageCredentialsKey")
            }
        return ret.thenReturn(TaskResult.OK)
    }

    private fun determineArchiverName(type: String): String {
        // todo 根据文件类型选择压缩算法
        if (!archiveProperties.compress.enabledCompress) {
            return EmptyArchiver.NAME
        }
        return XZArchiver.NAME
    }

    private fun chooseArchiver(name: String): Archiver {
        return when (name) {
            XZArchiver.NAME -> xzArchiver
            else -> emptyArchiver
        }
    }

    fun getKey(name: String, archiveName: String): String {
        return name.plus(chooseArchiver(archiveName).getSuffix())
    }

    fun getStorageCredentials(key: String?): StorageCredentials {
        return if (key == null) {
            archiveProperties.defaultCredentials.cos
        } else {
            val credentialsProperties = archiveProperties.extraCredentialsConfig[key]
            if (credentialsProperties == null) {
                return archiveProperties.defaultCredentials.cos
            }
            credentialsProperties.cos.apply { this.key = key }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveManager::class.java)
    }
}
