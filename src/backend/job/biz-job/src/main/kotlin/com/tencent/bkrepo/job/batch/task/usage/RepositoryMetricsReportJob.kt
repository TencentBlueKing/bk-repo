package com.tencent.bkrepo.job.batch.task.usage

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metrics.constant.LABEL_PROJECT
import com.tencent.bkrepo.common.metrics.constant.LABEL_REPOSITORY
import com.tencent.bkrepo.common.metrics.constant.LABEL_REPO_TYPE
import com.tencent.bkrepo.common.metrics.constant.REPOSITORY_ARTIFACT_COUNT
import com.tencent.bkrepo.common.metrics.constant.REPOSITORY_ARTIFACT_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.REPOSITORY_ARTIFACT_SIZE_BYTES
import com.tencent.bkrepo.common.metrics.constant.REPOSITORY_ARTIFACT_SIZE_BYTES_DESC
import com.tencent.bkrepo.common.metrics.constant.REPOSITORY_QUOTA_BYTES
import com.tencent.bkrepo.common.metrics.constant.REPOSITORY_QUOTA_BYTES_DESC
import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.enums.DataModel
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.RepositoryMetricsReportJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import com.tencent.bkrepo.job.pojo.project.TRepoMetrics
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import kotlin.reflect.KClass

/**
 * 仓库级别指标报告任务
 * 生成制品个数、用量和配额指标
 */
@Component
class RepositoryMetricsReportJob(
    val properties: RepositoryMetricsReportJobProperties,
    private val customMetricsExporter: CustomMetricsExporter? = null
) : DefaultContextMongoDbJob<TProjectMetrics>(properties) {

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME_PROJECT_METRICS)
    }

    override fun buildQuery(): Query {
        return Query(
            where(TProjectMetrics::createdDate).`is`(LocalDate.now().atStartOfDay())
        )
    }

    override fun run(row: TProjectMetrics, collectionName: String, context: JobContext) {
        generateRepositoryMetrics(row)
    }

    override fun mapToEntity(row: Map<String, Any?>): TProjectMetrics {
        return TProjectMetrics(
            projectId = row[TProjectMetrics::projectId.name].toString(),
            nodeNum = row[TProjectMetrics::nodeNum.name].toString().toLongOrNull() ?: 0,
            capSize = row[TProjectMetrics::capSize.name].toString().toLongOrNull() ?: 0,
            repoMetrics = row[TProjectMetrics::repoMetrics.name]?.toJsonString()
                ?.readJsonString<List<TRepoMetrics>>() ?: emptyList(),
            createdDate = TimeUtils.parseMongoDateTimeStr(row[TProjectMetrics::createdDate.name].toString()),
            active = row[TProjectMetrics::active.name].toString().toBoolean(),
            projectStatus = row[TProjectMetrics::projectStatus.name]?.toString()?.toBoolean()
        )
    }

    override fun entityClass(): KClass<TProjectMetrics> {
        return TProjectMetrics::class
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    /**
     * 生成仓库级别的指标
     * 包括：制品个数、制品用量、仓库配额
     */
    private fun generateRepositoryMetrics(projectMetrics: TProjectMetrics) {
        customMetricsExporter?.let { exporter ->
            projectMetrics.repoMetrics.forEach { repoMetric ->
                // 查询仓库的配额信息
                val repoQuota = getRepositoryQuota(projectMetrics.projectId, repoMetric.repoName)

                // 生成制品个数指标
                generateArtifactCountMetric(exporter, projectMetrics.projectId, repoMetric)

                // 生成制品用量指标
                generateArtifactSizeMetric(exporter, projectMetrics.projectId, repoMetric)

                // 生成仓库配额指标
                generateRepositoryQuotaMetric(exporter, projectMetrics.projectId, repoMetric.repoName, repoQuota)
            }
        }
    }

    /**
     * 生成制品个数指标
     * 维度：项目、仓库、仓库类型
     */
    private fun generateArtifactCountMetric(
        exporter: CustomMetricsExporter,
        projectId: String,
        repoMetric: TRepoMetrics,
    ) {
        val labels = mutableMapOf<String, String>().apply {
            put(LABEL_PROJECT, projectId)
            put(LABEL_REPOSITORY, repoMetric.repoName)
            put(LABEL_REPO_TYPE, repoMetric.type)
        }
        val artifactCountItem = MetricsItem(
            name = REPOSITORY_ARTIFACT_COUNT,
            help = REPOSITORY_ARTIFACT_COUNT_DESC,
            dataModel = DataModel.DATAMODEL_GAUGE,
            keepHistory = true,
            value = repoMetric.num.toDouble(),
            labels = labels
        )
        exporter.reportMetrics(artifactCountItem)
    }

    /**
     * 生成制品用量指标
     * 维度：项目、仓库、仓库类型
     */
    private fun generateArtifactSizeMetric(
        exporter: CustomMetricsExporter,
        projectId: String,
        repoMetric: TRepoMetrics,
    ) {
        val labels = mutableMapOf<String, String>().apply {
            put(LABEL_PROJECT, projectId)
            put(LABEL_REPOSITORY, repoMetric.repoName)
            put(LABEL_REPO_TYPE, repoMetric.type)
        }
        val artifactSizeItem = MetricsItem(
            name = REPOSITORY_ARTIFACT_SIZE_BYTES,
            help = REPOSITORY_ARTIFACT_SIZE_BYTES_DESC,
            dataModel = DataModel.DATAMODEL_GAUGE,
            keepHistory = true,
            value = repoMetric.size.toDouble(),
            labels = labels
        )
        exporter.reportMetrics(artifactSizeItem)
    }

    /**
     * 生成仓库配额指标
     * 维度：项目、仓库
     */
    private fun generateRepositoryQuotaMetric(
        exporter: CustomMetricsExporter,
        projectId: String,
        repoName: String,
        quota: Long?,
    ) {
        val labels = mutableMapOf<String, String>().apply {
            put(LABEL_PROJECT, projectId)
            put(LABEL_REPOSITORY, repoName)
        }

        // 如果配额为空，则设置为-1表示未配置
        val quotaValue = quota?.toDouble() ?: -1.0

        val quotaItem = MetricsItem(
            name = REPOSITORY_QUOTA_BYTES,
            help = REPOSITORY_QUOTA_BYTES_DESC,
            dataModel = DataModel.DATAMODEL_GAUGE,
            keepHistory = true,
            value = quotaValue,
            labels = labels
        )
        exporter.reportMetrics(quotaItem)
    }

    /**
     * 查询仓库的配额信息
     */
    private fun getRepositoryQuota(projectId: String, repoName: String): Long? {
        return try {
            val query = Query(
                where(TRepository::projectId).isEqualTo(projectId)
                    .and(TRepository::name).isEqualTo(repoName)
                    .and(TRepository::deleted).isEqualTo(null)
            )
            val repository = mongoTemplate.findOne(query, TRepository::class.java, COLLECTION_NAME_REPOSITORY)
            repository?.quota
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepositoryMetricsReportJob::class.java)
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
        private const val COLLECTION_NAME_REPOSITORY = "repository"
    }
}
