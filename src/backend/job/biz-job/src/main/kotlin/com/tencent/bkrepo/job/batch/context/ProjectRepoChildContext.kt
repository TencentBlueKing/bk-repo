package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.node.NodeStatCompositeMongoDbBatchJob
import com.tencent.bkrepo.job.batch.node.ProjectRepoStatChildJob
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import org.apache.commons.io.FileUtils
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class ProjectRepoChildContext(
    parentContent: JobContext,
    var metrics: ConcurrentHashMap<String, ProjectMetrics> = ConcurrentHashMap(),
    var statDate: LocalDateTime
) : ChildJobContext(parentContent) {

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val EXTENSION_NONE = "none"
        private const val TO_GIGABYTE = 1024 * 1024 * 1024
        private const val MAX_EXTENSION_LENGTH = 20
        private const val B_0 = 0L
        private const val MB_100 = (100 * FileUtils.ONE_MB)
        private const val MB_500 = (500 * FileUtils.ONE_MB)
        private const val GB_1 = FileUtils.ONE_GB
        private const val GB_10 = (10 * FileUtils.ONE_GB)
        private val sizeRange = listOf(GB_10, GB_1, MB_500, MB_100, B_0)
    }

    data class ProjectMetrics(
        val projectId: String,
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder(),
        val repoMetrics: ConcurrentHashMap<String, RepoMetrics> = ConcurrentHashMap()
    ) {
        fun addRepoMetrics(row: NodeStatCompositeMongoDbBatchJob.Node) {
            val credentialsKey = RepositoryCommonUtils
                .getRepositoryDetail(row.projectId, row.repoName)
                .storageCredentials
                ?.key ?: "default"
            val repo = repoMetrics.getOrPut(row.repoName) { RepoMetrics(row.repoName, credentialsKey) }
            if (!row.folder) {
                repo.size.add(row.size)
            }
            repo.num.increment()
            repo.addFolderMetrics(row)
            repo.addExtensionMetrics(row)
            repo.addSizeDistributionMetrics(row)
        }

        fun toDO(statDate: LocalDateTime = LocalDateTime.now()): ProjectRepoStatChildJob.TProjectMetrics {
            logger.info("project: [${projectId}], size: [${capSize.toLong()}]")
            val repoMetrics = ArrayList<ProjectRepoStatChildJob.TRepoMetrics>(repoMetrics.size)
            this.repoMetrics.values.forEach { repo ->
                val num = repo.num.toLong()
                val size = repo.size.toLong()
                // 有效仓库的统计数据
                if (num != 0L && size != 0L) {
                    repoMetrics.add(repo.toDO())
                }
            }

            return ProjectRepoStatChildJob.TProjectMetrics(
                projectId = projectId,
                nodeNum = nodeNum.toLong(),
                capSize = capSize.toLong(),
                repoMetrics = repoMetrics,
                createdDate = statDate
            )
        }
    }

    data class RepoMetrics(
        val repoName: String,
        val credentialsKey: String = "default",
        var size: LongAdder = LongAdder(),
        var num: LongAdder = LongAdder(),
        var folderMetrics: ConcurrentHashMap<String, FolderMetric> = ConcurrentHashMap(),
        var extensionMetrics: ConcurrentHashMap<String, ExtensionMetric> = ConcurrentHashMap(),
        var sizeDistributionMetrics: ConcurrentHashMap<String, LongAdder> = ConcurrentHashMap()
    ) {
        init {
            sizeRange.forEach { sizeDistributionMetrics[it.toString()] = LongAdder() }
        }

        fun addFolderMetrics(row: NodeStatCompositeMongoDbBatchJob.Node) {
            val firstLevelPath: String = if (PathUtils.isRoot(row.path)) {
                if (row.folder) {
                    row.fullPath
                } else {
                    PathUtils.UNIX_SEPARATOR.toString()
                }
            } else {
                PathUtils.resolveFirstLevelFolder(PathUtils.normalizeFullPath(row.path))
            }
            val metric = folderMetrics.getOrPut(firstLevelPath) { FolderMetric(firstLevelPath) }
            metric.nodeNum.increment()
            if (!row.folder) {
                metric.capSize.add(row.size)
            }
        }

        fun addExtensionMetrics(row: NodeStatCompositeMongoDbBatchJob.Node) {
            if (row.folder) {
                return
            }

            val ext = extension(row.name)
            val metric = extensionMetrics.getOrPut(ext) { ExtensionMetric(ext) }
            metric.nodeNum.increment()
            metric.capSize.add(row.size)
        }

        fun addSizeDistributionMetrics(row: NodeStatCompositeMongoDbBatchJob.Node) {
            if (row.folder) {
                return
            }

            for (lowerLimit in sizeRange) {
                if (row.size > lowerLimit) {
                    sizeDistributionMetrics[lowerLimit.toString()]!!.increment()
                }
            }
        }

        fun toDO(): ProjectRepoStatChildJob.TRepoMetrics {
            return ProjectRepoStatChildJob.TRepoMetrics(
                repoName = repoName,
                credentialsKey = credentialsKey,
                size = size.toLong(),
                num = num.toLong()
            )
        }

        private fun extension(name: String): String {
            val trimName = name.trim()
            // 后缀为全数字时不记录
            if (trimName.all { it.isDigit() }) {
                return EXTENSION_NONE
            }
            val ext = trimName.substringAfterLast(".", EXTENSION_NONE)
            // 扩展名较长的不记录
            return if (ext.length > MAX_EXTENSION_LENGTH) {
                EXTENSION_NONE
            } else {
                ext
            }
        }
    }

    data class FolderMetric(
        var path: String,
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder()
    ) {
        fun toDO(
            projectId: String, repoName: String, credentialsKey: String,
            statDate: LocalDateTime = LocalDateTime.now()
        ): ProjectRepoStatChildJob.TFolderMetrics {
            return ProjectRepoStatChildJob.TFolderMetrics(
                projectId = projectId,
                repoName = repoName,
                credentialsKey = credentialsKey,
                folderPath = path,
                nodeNum = nodeNum.toLong(),
                capSize = capSize.toLong(),
                createdDate = statDate
            )
        }
    }

    data class ExtensionMetric(
        var ext: String,
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder()
    ) {
        fun toDO(projectId: String, repoName: String): ProjectRepoStatChildJob.TFileExtensionMetrics {
            return ProjectRepoStatChildJob.TFileExtensionMetrics(
                projectId = projectId,
                repoName = repoName,
                extension = ext,
                num = nodeNum.toLong(),
                size = capSize.toLong()
            )
        }
    }
}
