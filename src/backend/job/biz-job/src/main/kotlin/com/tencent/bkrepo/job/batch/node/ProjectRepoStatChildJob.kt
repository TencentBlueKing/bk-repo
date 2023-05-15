package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class ProjectRepoStatChildJob(
    properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun onParentJobStart(context: ChildJobContext) {
        logger.info("start to stat project metrics")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is ProjectRepoChildContext)
        context.metrics
            .getOrPut(row.projectId) { ProjectMetrics(row.projectId) }
            .apply {
                capSize.add(row.size)
                nodeNum.increment()
                val credentialsKey = RepositoryCommonUtils
                    .getRepositoryDetail(row.projectId, row.repoName)
                    .storageCredentials
                    ?.key ?: "default"
                val repo = repoMetrics.getOrPut(row.repoName) { RepoMetrics(row.repoName, credentialsKey) }
                repo.size.add(row.size)
                repo.num.increment()
            }
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is ProjectRepoChildContext)
        val projectMetricsList = convert(context.metrics.values)

        // 数据写入mongodb统计表
        mongoTemplate.remove(Query(), COLLECTION_NAME)
        logger.info("start to insert  mongodb metrics ")
        mongoTemplate.insert(projectMetricsList, COLLECTION_NAME)
        logger.info("stat project metrics done")
    }

    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return ProjectRepoChildContext(parentJobContext)
    }

    private fun convert(projectMetricsList: Collection<ProjectMetrics>): List<TProjectMetrics> {
        val tProjectMetricsList = ArrayList<TProjectMetrics>(projectMetricsList.size)
        for (projectMetrics in projectMetricsList) {
            val projectNodeNum = projectMetrics.nodeNum.toLong()
            val projectCapSize = projectMetrics.capSize.toLong()
            if (projectNodeNum == 0L || projectCapSize == 0L) {
                // 只统计有效项目数据
                continue
            }
            val repoMetrics = ArrayList<TRepoMetrics>(projectMetrics.repoMetrics.size)
            projectMetrics.repoMetrics.values.forEach { repo ->
                val num = repo.num.toLong()
                val size = repo.size.toLong()
                // 有效仓库的统计数据
                if (num != 0L && size != 0L) {
                    logger.info("project : [${projectMetrics.projectId}],repo: [${repo.repoName}],size:[$repo]")
                    repoMetrics.add(TRepoMetrics(repo.repoName, repo.credentialsKey, size / TO_GIGABYTE, num))
                }
            }
            tProjectMetricsList.add(
                TProjectMetrics(
                    projectMetrics.projectId, projectNodeNum, projectCapSize / TO_GIGABYTE, repoMetrics
                )
            )
        }
        return tProjectMetricsList
    }

    class ProjectRepoChildContext(
        parentContent: JobContext,
        var metrics: ConcurrentHashMap<String, ProjectMetrics> = ConcurrentHashMap(),
    ) : ChildJobContext(parentContent)

    data class ProjectMetrics(
        val projectId: String,
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder(),
        val repoMetrics: ConcurrentHashMap<String, RepoMetrics> = ConcurrentHashMap()
    )

    data class RepoMetrics(
        val repoName: String,
        val credentialsKey: String = "default",
        var size: LongAdder = LongAdder(),
        var num: LongAdder = LongAdder()
    )

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


    companion object {
        private const val TO_GIGABYTE = 1024 * 1024 * 1024
        private const val COLLECTION_NAME = "project_metrics"
        private val logger = LoggerFactory.getLogger(ProjectRepoStatChildJob::class.java)
    }
}
