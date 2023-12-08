package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveFileQueryHelper
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.monitor.Throughput
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 数据恢复任务
 * */
@Component
class RestoreJob(
    private val archiveFileRepository: ArchiveFileRepository,
    private val storageService: StorageService,
    private val archiveProperties: ArchiveProperties,
    private val archiveFileDao: ArchiveFileDao,
) {
    private val cosClient = CosClient(archiveProperties.cos)
    private val useCmd = ArchiveUtils.supportXZCmd()
    private val workDir = archiveProperties.workDir
    private val tempPath: Path = Paths.get(workDir, "temp")
    private val compressedPath: Path = Paths.get(workDir, "compressed")
    private val filesPath: Path = Paths.get(workDir, "files")
    val context: JobContext = JobContext()
    private val monitorId = "restore-job"

    init {
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath)
        }
        if (!Files.exists(filesPath)) {
            Files.createDirectories(filesPath)
        }
        if (!Files.exists(compressedPath)) {
            Files.createDirectories(compressedPath)
        }
    }

    @Scheduled(fixedDelay = 12, timeUnit = TimeUnit.HOURS)
    fun restore() {
        logger.info("Begin restore.")
        val startAt = System.nanoTime()
        context.reset()
        ArchiveUtils.monitor.addMonitor(monitorId, context)
        var lastId = MIN_OBJECT_ID
        var query = ArchiveFileQueryHelper
            .buildQuery(ArchiveStatus.WAIT_TO_RESTORE, lastId, archiveProperties.queryLimit)
        var files = archiveFileDao.find(query).toMutableList()
        while (files.isNotEmpty()) {
            logger.info("Find ${files.size} file to restore.")
            lastId = files.last().id!!
            files.shuffled()
            restoreFiles(files)
            query =
                ArchiveFileQueryHelper.buildQuery(ArchiveStatus.WAIT_TO_RESTORE, lastId, archiveProperties.queryLimit)
            files = archiveFileDao.find(query).toMutableList()
        }
        ArchiveUtils.monitor.removeMonitor(monitorId)
        val stopAt = System.nanoTime()
        val throughput = Throughput(context.totalSize.get(), stopAt - startAt)
        logger.info("End restore,summary: $context $throughput.")
    }

    private fun restoreFiles(files: List<TArchiveFile>) {
        files.forEach {
            val criteria = Criteria.where(ID).isEqualTo(ObjectId(it.id))
                .and(TArchiveFile::status.name).isEqualTo(ArchiveStatus.WAIT_TO_RESTORE.name)
            val update = Update().set(TArchiveFile::status.name, ArchiveStatus.RESTORING.name)
                .set(TArchiveFile::lastModifiedDate.name, LocalDateTime.now())
            val result = archiveFileDao.updateFirst(Query.query(criteria), update)
            if (result.modifiedCount != 1L) {
                logger.info("${it.key()} already start restore.")
                return
            }
            context.total.incrementAndGet()
            context.totalSize.addAndGet(it.size)
            try {
                logger.info("Start restore file ${it.key()}")
                restoreFile(it)
            } catch (e: Exception) {
                it.status = ArchiveStatus.RESTORE_FAILED
                updateArchiveFile(it)
                context.failed.incrementAndGet()
                logger.error("Restore file ${it.key()} error: ", e)
            }
        }
    }

    private fun restoreFile(file: TArchiveFile) {
        with(file) {
            val key = "$sha256$XZ_SUFFIX"
            val checkObjectExistRequest = CheckObjectExistRequest(key)
            val restored = cosClient.checkObjectRestore(checkObjectExistRequest)
            if (!restored) {
                logger.info("$key is not ready.")
                status = ArchiveStatus.WAIT_TO_RESTORE
                updateArchiveFile(this)
                return
            }
            val filePath = filesPath.resolve(sha256)
            try {
                download(key, filePath)
                // 存储文件
                val artifactFile = filePath.toFile().toArtifactFile(true)
                val receiveSha256 = artifactFile.getFileSha256()
                if (receiveSha256 != sha256) {
                    error("File[${key()}] broken,receive $receiveSha256.)")
                }
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                storageService.store(sha256, artifactFile, storageCredentials)
                val tp = Throughput(size, Duration.between(lastModifiedDate, LocalDateTime.now()).toNanos())
                val event = FileRestoredEvent(sha256, storageCredentialsKey, tp)
                SpringContextUtils.publishEvent(event)
                status = ArchiveStatus.RESTORED
                updateArchiveFile(this)
                context.success.incrementAndGet()
                logger.info("Success to restore file ${this.key()}.")
            } finally {
                Files.deleteIfExists(filePath)
                logger.info("Success delete temp file $filePath")
            }
        }
    }

    private fun download(key: String, filePath: Path) {
        val tempFilePath = getTempPath()
        val xzFilePath = compressedPath.resolve(key)
        try {
            val cosRequest = GetObjectRequest(key)
            val xzFileInput = cosClient.getObjectByChunked(cosRequest).inputStream ?: error("miss file data $key.")
            // 先下载文件到本地
            xzFileInput.use { input ->
                Files.newOutputStream(tempFilePath).use { output ->
                    input.copyTo(output)
                }
            }
            Files.move(tempFilePath, xzFilePath)
            // 解压
            unCompressFile(xzFilePath, filePath)
        } finally {
            Files.deleteIfExists(tempFilePath)
            Files.deleteIfExists(xzFilePath)
        }
    }

    private fun getTempPath(): Path {
        return tempPath.resolve(StringPool.randomStringByLongValue(prefix = "restore_", suffix = ".temp"))
    }

    private fun updateArchiveFile(file: TArchiveFile) {
        file.lastModifiedDate = LocalDateTime.now()
        archiveFileRepository.save(file)
    }

    private fun unCompressFile(src: Path, target: Path) {
        val path = src.toAbsolutePath()
        val cmd = mutableListOf("xz", "-d", path.toString())
        ArchiveUtils.runCmd(cmd)
        val filePath = Paths.get(path.toString().removeSuffix(XZ_SUFFIX))
        Files.move(filePath, target)
        logger.info("Move $filePath to $target.")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RestoreJob::class.java)
    }
}
