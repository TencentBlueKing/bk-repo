package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.CREATED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.ProjectRepoChildContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils.buildCacheKey
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDate
import java.time.LocalDateTime

class ProjectRepoStatChildJob(
    properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun onParentJobStart(context: ChildJobContext) {
        logger.info("start to stat project metrics")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is ProjectRepoChildContext)
        if (row.deleted != null) {
            return
        }
        val key = buildCacheKey(collectionName = collectionName, projectId = row.projectId)
        val metric = context.metrics.getOrPut(key) { ProjectRepoChildContext.ProjectMetrics(row.projectId) }
        if (!row.folder) {
            metric.capSize.add(row.size)
        }
        metric.nodeNum.increment()
        metric.addRepoMetrics(row)
    }


    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is ProjectRepoChildContext)

        val prefix = buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        val storedKeys = mutableListOf<String>()
        for (entry in context.metrics) {
            if (!entry.key.contains(prefix)) continue
            storeMetrics(context.statDate, entry.value)
            storedKeys.add(entry.key)
        }
        storedKeys.forEach {
            context.metrics.remove(it)
        }
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is ProjectRepoChildContext)
        logger.info("folder stat finished")
    }

    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return ProjectRepoChildContext(parentJobContext, statDate = LocalDate.now().atStartOfDay())
    }


    private fun storeMetrics(
        statDate: LocalDateTime,
        projectMetric: ProjectRepoChildContext.ProjectMetrics
    ) {
        val folderMetrics = ArrayList<TFolderMetrics>()
        val extensionMetrics = ArrayList<TFileExtensionMetrics>()
        val sizeDistributionMetrics = ArrayList<TSizeDistributionMetrics>()

        val projectId = projectMetric.projectId
        projectMetric.repoMetrics.values.forEach { repoMetric ->
            val repoName = repoMetric.repoName
            repoMetric.folderMetrics.values.forEach {
                folderMetrics.add(it.toDO(projectId, repoName, repoMetric.credentialsKey, statDate))
            }
            repoMetric.extensionMetrics.values.forEach {
                extensionMetrics.add(it.toDO(projectId, repoName))
            }
            val sizeDistribution = repoMetric.sizeDistributionMetrics.mapValues { it.value.toLong() }
            sizeDistributionMetrics.add(TSizeDistributionMetrics(projectId, repoName, sizeDistribution))
        }
        // insert project repo metrics
        var criteria = Criteria.where(PROJECT).isEqualTo(projectId).and(CREATED_DATE).isEqualTo(statDate)

        mongoTemplate.remove(Query(criteria), COLLECTION_NAME_PROJECT_METRICS)
        logger.info("start to insert project's metrics ")
        mongoTemplate.insert(projectMetric.toDO(statDate), COLLECTION_NAME_PROJECT_METRICS)
        logger.info("stat project metrics done")

        criteria = Criteria.where(PROJECT).isEqualTo(projectId)

        // insert folder metrics
        mongoTemplate.remove(Query(criteria), COLLECTION_NAME_FOLDER_METRICS)
        logger.info("start to insert folder's metrics ")
        mongoTemplate.insert(folderMetrics, COLLECTION_NAME_FOLDER_METRICS)
        logger.info("stat folder metrics done")

        // insert ext metrics
        mongoTemplate.remove(Query(criteria), COLLECTION_NAME_EXTENSION_METRICS)
        logger.info("start to insert extension's metrics ")
        mongoTemplate.insert(extensionMetrics, COLLECTION_NAME_EXTENSION_METRICS)
        logger.info("stat ext metrics done")

        // insert size distribution metrics
        mongoTemplate.remove(Query(criteria), COLLECTION_NAME_SIZE_DISTRIBUTION_METRICS)
        logger.info("start to insert size distribution metrics ")
        mongoTemplate.insert(sizeDistributionMetrics, COLLECTION_NAME_SIZE_DISTRIBUTION_METRICS)
        logger.info("stat size distribution metrics done")
    }

    data class TProjectMetrics(
        var projectId: String,
        var nodeNum: Long,
        var capSize: Long,
        val repoMetrics: List<TRepoMetrics>,
        val createdDate: LocalDateTime? = LocalDateTime.now()
    )

    data class TRepoMetrics(
        val repoName: String,
        val credentialsKey: String? = "default",
        val size: Long,
        val num: Long
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

    data class TFileExtensionMetrics(
        val projectId: String,
        val repoName: String,
        val extension: String,
        val num: Long,
        val size: Long
    )

    data class TSizeDistributionMetrics(
        val projectId: String,
        val repoName: String,
        val sizeDistribution: Map<String, Long>
    )

    companion object {
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
        private const val COLLECTION_NAME_FOLDER_METRICS = "folder_metrics"
        private const val COLLECTION_NAME_EXTENSION_METRICS = "file_extension_metrics"
        private const val COLLECTION_NAME_SIZE_DISTRIBUTION_METRICS = "size_distribution_metrics"
        private val logger = LoggerHolder.jobLogger
    }
}
