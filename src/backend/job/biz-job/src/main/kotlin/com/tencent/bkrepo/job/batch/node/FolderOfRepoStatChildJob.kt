package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import javax.management.Query

/**
 *统计仓库一级目录数据
 */
class FolderOfRepoStatChildJob(
    properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun onParentJobStart(context: ChildJobContext) {
        logger.info("start to stat folder metrics")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is FolderOfRepoStatChildJobContext)
        if (row.deleted != null) {
            return
        }
        val key: String = if (PathUtils.isRoot(row.path)) {
            if (row.folder) {
                row.fullPath
            } else {
                PathUtils.UNIX_SEPARATOR.toString()
            }
        } else {
            PathUtils.resolveFirstLevelFolder(PathUtils.normalizeFullPath(row.path))
        }
        val metric = context.metrics.getOrPut(FOLDER_KEY_FORMAT.format(row.projectId, row.repoName, key)) {
            FolderMetric(row.projectId, row.repoName, key)
        }
        metric.nodeNum.increment()
        metric.capSize.add(row.size)
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is FolderOfRepoStatChildJobContext)
        val folderMetricsList = convert(context.metrics.values)
        // 数据写入mongodb统计表
        mongoTemplate.remove(Query(), COLLECTION_NAME)
        logger.info("start to insert folder's metrics ")
        mongoTemplate.insert(folderMetricsList, COLLECTION_NAME)
        logger.info("stat folder metrics done")
    }

    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return FolderOfRepoStatChildJobContext(parentJobContext)
    }

    private fun convert(folderMetricsList: Collection<FolderMetric>): List<TFolderMetrics> {
        return folderMetricsList.map { folderMetrics ->
            val credentialsKey = RepositoryCommonUtils
                .getRepositoryDetail(folderMetrics.projectId, folderMetrics.repoName)
                .storageCredentials
                ?.key
                ?: "default"
            TFolderMetrics(
                projectId = folderMetrics.projectId,
                repoName = folderMetrics.repoName,
                credentialsKey = credentialsKey,
                folderPath = folderMetrics.path,
                nodeNum = folderMetrics.nodeNum.toLong(),
                capSize = folderMetrics.capSize.toLong()
            )
        }
    }

    class FolderOfRepoStatChildJobContext(
        parentJobContext: JobContext,
        var metrics: ConcurrentHashMap<String, FolderMetric> = ConcurrentHashMap(),
    ) : ChildJobContext(parentJobContext)

    data class FolderMetric(
        var projectId: String,
        var repoName: String,
        var path: String,
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder()
    )

    data class TFolderMetrics(
        val projectId: String,
        val repoName: String,
        var credentialsKey: String? = "default",
        val folderPath: String,
        val nodeNum: Long,
        val capSize: Long,
        val createdDate: LocalDateTime? = LocalDateTime.now()
    )

    companion object {
        private const val COLLECTION_NAME = "folder_metrics"
        private const val FOLDER_KEY_FORMAT = "%s|%s|%s"
        private val logger = LoggerFactory.getLogger(FolderOfRepoStatChildJob::class.java)
    }
}
