package com.tencent.bkrepo.archive.job.archive

import com.google.common.hash.HashCode
import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * 文件下载器
 * */
class FileDownloader(
    /**
     * 归档实例
     * */
    private val cosClient: CosClient,
    /**
     * 工作路径
     * */
    private val workPath: String,
    /**
     * 源数据存储服务
     * */
    private val storageService: StorageService,
    /**
     * 磁盘空闲阈值，当磁盘剩余容量小于阈值时，下载器将停止工作，直到磁盘容量恢复到阈值之上。
     * */
    private val diskThreshold: Long,
) :
    AbstractArchiveFileWrapperCallback(), DiskHealthObserver {
    /**
     * 临时目录
     * */
    private val tempPath: Path = Paths.get(workPath, "temp")

    /**
     * 完整文件目录
     * */
    private val filesPath: Path = Paths.get(workPath, "files")

    /**
     * 磁盘是否还有空闲空间
     * */
    private var hasFreeSpace = true

    init {
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath)
        }
        if (!Files.exists(filesPath)) {
            Files.createDirectories(filesPath)
        }
    }

    override fun process(fileWrapper: ArchiveFileWrapper): Mono<ArchiveFileWrapper> {
        return Mono.create {
            download(fileWrapper)
            it.success(fileWrapper)
        }
    }

    /**
     * 下载文件到指定路径
     * 如果下载成功，则设置文件源路径，压缩器会根据源文件路径，进行文件压缩
     * */
    private fun download(fileWrapper: ArchiveFileWrapper) {
        with(fileWrapper.archiveFile) {
            // 正式开始归档流程
            fileWrapper.startTime = LocalDateTime.now()
            // 查看归档存储是否已经存在，跳过上传
            val key = "$sha256$XZ_SUFFIX"
            val checkObjectExistRequest = CheckObjectExistRequest(key)
            val exist = cosClient.checkArchiveObjectExist(checkObjectExistRequest)
            if (exist) {
                logger.info("$key already exist in archive cos.")
                return
            }
            if (!hasFreeSpace) {
                // 磁盘不够时，等待压缩完成释放磁盘。
                val wait = waitFor(MAX_WAIT_TIME)
                if (!wait) {
                    throw TimeoutException()
                }
            }
            val tempFilePath = getTempPath()
            try {
                // 下载文件
                val messageDigest = MessageDigest.getInstance("SHA-256")
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                val fileInput = storageService.load(sha256, Range.full(size), storageCredentials)
                    ?: error("miss file data $sha256 in ${storageCredentials.key}")
                val throughput = measureThroughput {
                    DigestInputStream(fileInput, messageDigest).use { it.writeTo(tempFilePath) }
                }
                logger.info("Success download $sha256 on ${storageCredentials.key},$throughput.")
                val digestBytes = messageDigest.digest()
                val receiveSha256 = HashCode.fromBytes(digestBytes).toString()
                if (receiveSha256 != sha256) {
                    error("File[${key()}] broken,receive $receiveSha256.\")")
                }
                val filePath = filesPath.resolve(sha256)
                Files.move(tempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING)
                logger.info("Move $tempFilePath to $filePath.")
                // 设置文件源路径
                fileWrapper.srcFilePath = filePath
            } finally {
                Files.deleteIfExists(tempFilePath)
                logger.info("Delete temp file $tempFilePath")
            }
        }
    }

    private fun InputStream.writeTo(tempFilePath: Path): Long {
        return Files.newOutputStream(tempFilePath).use { this.copyTo(it) }
    }

    private fun getTempPath(): Path {
        return tempPath.resolve(StringPool.randomStringByLongValue(prefix = "archive_", suffix = ".temp"))
    }

    override fun healthy() {
        if (!hasFreeSpace) {
            hasFreeSpace = true
            logger.warn("Disk change to health")
        }
    }

    override fun unHealthy() {
        if (hasFreeSpace) {
            hasFreeSpace = false
            logger.warn("Disk change to unHealthy")
        }
    }

    private fun waitFor(timeout: Long): Boolean {
        var waitTime = 0L
        while (!hasFreeSpace && waitTime < timeout) {
            val diskFreeInBytes = Paths.get(workPath).toFile().usableSpace
            logger.warn("No more space for download, free: $diskFreeInBytes ,threshold: $diskThreshold.")
            Thread.sleep(SLEEP_TIME)
            waitTime += SLEEP_TIME
        }
        return waitTime < timeout
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileDownloader::class.java)
        private const val SLEEP_TIME = 60 * 1000L // 1 min
        private const val MAX_WAIT_TIME = 60 * 60 * 1000L // 1 hour
    }
}
