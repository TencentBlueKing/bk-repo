/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.CUSTOM
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.stream.constant.BinderType
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.ProjectReportJobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.ProjectMetricsReport2BkbaseJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import com.tencent.bkrepo.job.pojo.project.TRepoMetrics
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 导出项目运营数据到bkbase
 */
@Component
@EnableConfigurationProperties(ProjectMetricsReport2BkbaseJobProperties::class)
class ProjectMetricsReport2BkbaseJob(
    val properties: ProjectMetricsReport2BkbaseJobProperties,
    val messageSupplier: MessageSupplier
) : DefaultContextMongoDbJob<TProjectMetrics>(properties)  {
    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME_PROJECT_METRICS)
    }

    override fun buildQuery(): Query {
        return Query(
            where(TProjectMetrics::createdDate).`is`(LocalDate.now().atStartOfDay())
        )
    }

    override fun run(row: TProjectMetrics, collectionName: String, context: JobContext) {
        require(context is ProjectReportJobContext)
        val query = Query(where(ProjectInfo::name).isEqualTo(row.projectId))
        val projectInfo = mongoTemplate.find(query, ProjectInfo::class.java, COLLECTION_NAME_PROJECT)
            .firstOrNull() ?: return
        val storageMetrics = calculateRepoStorage(row, projectInfo, context.statTime)
        messageSupplier.delegateToSupplier(storageMetrics, topic = TOPIC, binderType = BinderType.KAFKA)
    }

    override fun mapToEntity(row: Map<String, Any?>): TProjectMetrics {
        return TProjectMetrics(
            projectId = row[TProjectMetrics::projectId.name].toString(),
            nodeNum = row[TProjectMetrics::nodeNum.name].toString().toLongOrNull() ?: 0,
            capSize = row[TProjectMetrics::capSize.name].toString().toLongOrNull() ?: 0,
            repoMetrics = row[TProjectMetrics::repoMetrics.name]?.toJsonString()
                ?.readJsonString<List<TRepoMetrics>>() ?: emptyList(),
            createdDate = TimeUtils.parseMongoDateTimeStr(row[TProjectMetrics::createdDate.name].toString())
        )
    }

    override fun entityClass(): KClass<TProjectMetrics> {
        return TProjectMetrics::class
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun createJobContext(): ProjectReportJobContext = ProjectReportJobContext(
        statTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    )


    private fun calculateRepoStorage(
        current: TProjectMetrics, project: ProjectInfo, statTime: LocalDateTime
    ): ProjectMetrics {
        val pipelineCapSize = filterByRepoName(current, PIPELINE)
        val customCapSize = filterByRepoName(current, CUSTOM)
        val helmRepoCapSize = filterByRepoType(current, RepositoryType.HELM.name)
        val dockerRepoCapSize = filterByRepoType(current, RepositoryType.DOCKER.name)
        return ProjectMetrics(
            projectId = current.projectId,
            nodeNum = current.nodeNum,
            capSize = current.capSize,
            createdDate = statTime,
            pipelineCapSize = pipelineCapSize,
            customCapSize = customCapSize,
            helmRepoCapSize = helmRepoCapSize,
            dockerRepoCapSize = dockerRepoCapSize,
            bgName = project.metadata.firstOrNull { it.key == ProjectMetadata.KEY_BG_NAME }?.value as? String,
            deptName = project.metadata.firstOrNull { it.key == ProjectMetadata.KEY_DEPT_NAME }?.value as? String,
            centerName = project.metadata.firstOrNull { it.key == ProjectMetadata.KEY_CENTER_NAME }?.value as? String,
            productId = project.metadata.firstOrNull { it.key == ProjectMetadata.KEY_PRODUCT_ID }?.value as? Int,
            enabled = project.metadata.firstOrNull { it.key == ProjectMetadata.KEY_ENABLED }?.value as? Boolean,
        )
    }


    private fun filterByRepoName(metric: TProjectMetrics?, repoName: String): Long {
        return metric?.repoMetrics?.firstOrNull { it.repoName == repoName }?.size ?: 0
    }

    private fun filterByRepoType(metric: TProjectMetrics?, repoType: String): Long {
        var sizeOfRepoType: Long = 0
        metric?.repoMetrics?.forEach { repo ->
            if (repo.type == repoType) {
                sizeOfRepoType += repo.size
            }
        }
        return sizeOfRepoType
    }

    data class ProjectMetrics(
        var projectId: String,
        var bgName: String?,
        var deptName: String?,
        var centerName: String?,
        var productId: Int?,
        var enabled: Boolean?,
        var nodeNum: Long,
        var capSize: Long,
        var pipelineCapSize: Long = 0,
        var customCapSize: Long = 0,
        var helmRepoCapSize: Long = 0,
        var dockerRepoCapSize: Long = 0,
        val createdDate: LocalDateTime,
    )

    data class ProjectInfo(
        val name: String,
        val metadata: List<ProjectMetadata> = emptyList(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectMetricsReport2BkbaseJob::class.java)
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
        private const val COLLECTION_NAME_PROJECT = "project"
        private const val TOPIC = "bkbase-bkrepo-project-storage-usage"
    }
}
