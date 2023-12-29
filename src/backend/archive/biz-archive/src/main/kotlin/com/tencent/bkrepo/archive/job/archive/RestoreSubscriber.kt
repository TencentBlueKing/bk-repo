package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.archive.event.FileRestoredEvent
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.job.BaseJobSubscriber
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime

/**
 * 数据恢复订阅者
 * 处理具体数据恢复
 * */
class RestoreSubscriber(
    private val cosClient: CosClient,
    private val archiveFileDao: ArchiveFileDao,
    private val storageService: StorageService,
    private val archiveFileRepository: ArchiveFileRepository,
    workDir: String,
) : BaseJobSubscriber<TArchiveFile>() {
    private val tempPath: Path = Paths.get(workDir, "temp")
    private val compressedPath: Path = Paths.get(workDir, "compressed")
    private val filesPath: Path = Paths.get(workDir, "files")

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

    override fun doOnNext(value: TArchiveFile) {
        with(value) {
            val tryLock = archiveFileDao.optimisticLock(
                value,
                TArchiveFile::status.name,
                ArchiveStatus.WAIT_TO_RESTORE.name,
                ArchiveStatus.RESTORING.name,
            )
            if (!tryLock) {
                logger.info("$sha256 already start restore.")
                return
            }
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
                logger.info("Start restore file $sha256")
                val beginAt = LocalDateTime.now()
                download(key, filePath)
                // 存储文件
                val artifactFile = filePath.toFile().toArtifactFile(true)
                val receiveSha256 = artifactFile.getFileSha256()
                if (receiveSha256 != sha256) {
                    error("File[${key()}] broken,receive $receiveSha256.)")
                }
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                storageService.store(sha256, artifactFile, storageCredentials)
                val tp = Throughput(size, Duration.between(beginAt, LocalDateTime.now()).toNanos())
                val event = FileRestoredEvent(sha256, storageCredentialsKey, tp)
                SpringContextUtils.publishEvent(event)
                status = ArchiveStatus.RESTORED
                updateArchiveFile(this)
                logger.info("Success to restore file ${this.key()}.")
            } catch (e: Exception) {
                value.status = ArchiveStatus.RESTORE_FAILED
                updateArchiveFile(value)
                logger.error("Restore file $sha256 error: ", e)
                throw e
            } finally {
                Files.deleteIfExists(filePath)
                logger.info("Success delete temp file $filePath")
            }
        }
    }

    override fun getSize(value: TArchiveFile): Long {
        return value.size
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
        private val logger = LoggerFactory.getLogger(RestoreSubscriber::class.java)
    }
}
