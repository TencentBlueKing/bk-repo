package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.FileSystemStorageStatJobProperties
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime

@Component
@EnableConfigurationProperties(FileSystemStorageStatJobProperties::class)
class FileSystemStorageStatJob(
    properties: FileSystemStorageStatJobProperties,
    private val storageProperties: StorageProperties,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val mongoTemplate: MongoTemplate
) : DefaultContextJob(properties) {
    override fun doStart0(jobContext: JobContext) {
        logger.info("start to stat the metrics of file system storage")
        mongoTemplate.remove(Query(), COLLECTION_NAME)
        folderStatAndStore()
        logger.info("stat metrics of file system storage done")
    }

    fun folderStatAndStore() {
        logger.info("Will start to collect the metrics for folders")
        val paths = when (storageProperties.type) {
            StorageType.FILESYSTEM -> findFileSystemPath()
            StorageType.INNERCOS -> findStoragePath()
            else -> emptyList()
        }
        paths.map {
            logger.info("Metrics of folders $it will be collected")
            val file = File(it)
            val metric = PathStatMetric(
                path = it,
                totalSpace = file.totalSpace,
                usableSpace = file.usableSpace
            )
            try {
                Files.walkFileTree(Paths.get(it), StoragePathStatVisitor(it, metric))
            } catch (ignore: NoSuchFileException) {
            }
            storeMetrics(metric)
        }
    }

    private fun storeMetrics(metric: PathStatMetric) {
        val folderMetricsList = mutableListOf<TPathStatMetric>()
        folderMetricsList.add(
            TPathStatMetric(
                path = metric.path,
                totalSize = metric.totalSize,
                totalFileCount = metric.totalFileCount,
                totalFolderCount = metric.totalFolderCount,
                totalSpace = metric.totalSpace,
                usedPercent = if (metric.totalSpace == 0L) {
                    0.0
                } else {
                    BigDecimal((metric.totalSpace - metric.usableSpace) / (metric.totalSpace * 1.0))
                        .setScale(4, RoundingMode.HALF_UP).toDouble()
                }
            )
        )
        folderMetricsList.addAll(
            metric.folders.map {
                TPathStatMetric(
                    path = it.key,
                    totalSize = it.value,
                    rootPath = metric.path
                )
            }
        )
        logger.info("start to insert the metrics of file system storage path ${metric.path}")
        mongoTemplate.insert(folderMetricsList, COLLECTION_NAME)
    }

    private fun findFileSystemPath(): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(getLocalPath(storageProperties.filesystem.cache, storageProperties.filesystem.upload))
        result.add(storageProperties.filesystem.path)
        return result
    }

    private fun findStoragePath(): Set<String> {
        val list = storageCredentialsClient.list().data ?: return emptySet()
        val default = storageProperties.defaultStorageCredentials()
        val result = mutableSetOf<String>()
        list.forEach {
            result.addAll(getLocalPath(it.cache, it.upload))
        }
        result.addAll(getLocalPath(default.cache, default.upload))
        return result
    }

    private fun getLocalPath(cache: CacheProperties, upload: UploadProperties): List<String> {
        return listOf(cache.path, upload.location)
    }

    data class TPathStatMetric(
        var path: String,
        var totalFileCount: Long = 0,
        var totalSize: Long = 0,
        var totalFolderCount: Long = 0,
        var totalSpace: Long? = null,
        var usedPercent: Double? = null,
        var rootPath: String? = null,
        val createdDate: LocalDateTime? = LocalDateTime.now()
    )

    data class PathStatMetric(
        var path: String,
        var totalFileCount: Long = 0,
        var totalSize: Long = 0,
        var totalFolderCount: Long = 0,
        var totalSpace: Long = 0,
        var usableSpace: Long = 0,
        var folders: MutableMap<String, Long> = mutableMapOf()
    )

    /**
     * 遍历目录统计其子目录以及文件相关信息
     */
    class StoragePathStatVisitor(
        private val rootPath: String,
        private val pathStatMetric: PathStatMetric
    ) : SimpleFileVisitor<Path>() {
        @Throws(IOException::class)
        override fun visitFile(filePath: Path, attributes: BasicFileAttributes): FileVisitResult {
            val file = filePath.toFile()
            pathStatMetric.totalFileCount += 1
            pathStatMetric.totalSize += file.length()
            PathUtils.resolveAncestor(filePath.toString()).forEach {
                if (it.startsWith(rootPath)) {
                    val temp = pathStatMetric.folders[it] ?: 0
                    pathStatMetric.folders[it] = temp.plus(file.length())
                }
            }
            return FileVisitResult.CONTINUE
        }

        @Throws(IOException::class)
        override fun postVisitDirectory(dirPath: Path, exc: IOException?): FileVisitResult {
            if (dirPath.startsWith(rootPath)) {
                pathStatMetric.totalFolderCount += 1
            }
            return FileVisitResult.CONTINUE
        }
    }

    companion object {
        private const val COLLECTION_NAME = "file_system_storage_metrics"
        private val logger = LoggerFactory.getLogger(FileSynchronizeJob::class.java)
    }
}
