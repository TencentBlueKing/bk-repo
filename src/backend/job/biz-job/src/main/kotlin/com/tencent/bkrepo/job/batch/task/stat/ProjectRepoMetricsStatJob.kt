/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.stat

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.CREATED_DATE
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PATH
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.ProjectRepoMetricsStatJobContext
import com.tencent.bkrepo.job.batch.task.archive.ArchiveNodeStatJob.Companion.ARCHIVE_STAT_INFO
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.config.properties.ProjectRepoMetricsStatJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import com.tencent.bkrepo.job.pojo.stat.StatNode
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 项目仓库指标统计任务
 */
open class ProjectRepoMetricsStatJob(
    val properties: ProjectRepoMetricsStatJobProperties,
    private val activeProjectService: ActiveProjectService,
    private val active: Boolean = true,
    private val separationTaskService: SeparationTaskService,
) : DefaultContextMongoDbJob<ProjectRepoMetricsStatJob.Repository>(properties) {

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_REPOSITORY_NAME)
    }

    override fun buildQuery(): Query = Query(Criteria.where(DELETED_DATE).`is`(null))

    override fun mapToEntity(row: Map<String, Any?>): Repository {
        return Repository(row)
    }

    override fun entityClass(): KClass<Repository> {
        return Repository::class
    }

    open fun statProjectCheck(
        projectId: String,
        context: ProjectRepoMetricsStatJobContext,
    ): Boolean = true

    override fun run(row: Repository, collectionName: String, context: JobContext) {
        require(context is ProjectRepoMetricsStatJobContext)
        with(row) {
            if (!statProjectCheck(projectId, context)) return

            val collectionList = mutableListOf<String>()
            collectionList.addAll(separationTaskService.findSeparationCollectionList(projectId))
            val nodeCollectionName = COLLECTION_NODE_PREFIX +
                MongoShardingUtils.shardingSequence(projectId, SHARDING_COUNT)
            collectionList.add(nodeCollectionName)

            val metric = getOrCreateMetric(context, projectId, nodeCollectionName)
            processCollections(collectionList, row, metric)
            // 减掉指定项目归档大小
            adjustArchiveMetricsIfNeeded(projectId, name, metric)
        }
    }

    private fun getOrCreateMetric(
        context: ProjectRepoMetricsStatJobContext,
        projectId: String,
        collectionName: String
    ): ProjectRepoMetricsStatJobContext.ProjectMetrics {
        val key = FolderUtils.buildCacheKey(collectionName = collectionName, projectId = projectId)
        return context.metrics.getOrPut(key) { ProjectRepoMetricsStatJobContext.ProjectMetrics(projectId) }
    }

    private fun processCollections(
        collections: List<String>,
        row: Repository,
        metric: ProjectRepoMetricsStatJobContext.ProjectMetrics
    ) {
        collections.forEach { collection ->
            val query = Query(
                Criteria.where(PROJECT).isEqualTo(row.projectId)
                    .and(REPO).isEqualTo(row.name)
                    .and(PATH).isEqualTo(PathUtils.ROOT)
                    .and(DELETED_DATE).isEqualTo(null)
            )
            mongoTemplate.find<StatNode>(query, collection).forEach {
                runRepoMetrics(metric, row, it)
            }
        }
    }

    private fun runRepoMetrics(
        metric: ProjectRepoMetricsStatJobContext.ProjectMetrics,
        repo: Repository,
        node: StatNode,
    ) {
        if (!node.folder) {
            metric.nodeNum.increment()
        } else {
            val nodeNum = node.nodeNum ?: 0
            metric.nodeNum.add(nodeNum)
        }
        metric.capSize.add(node.size)
        metric.addRepoMetrics(row = node, credentialsKey = repo.credentialsKey, repoType = repo.type)
    }

    private fun adjustArchiveMetricsIfNeeded(
        projectId: String,
        repoName: String,
        metric: ProjectRepoMetricsStatJobContext.ProjectMetrics
    ) {
        if (properties.ignoreArchiveProjects.contains(projectId)) {
            val (num, size) = getArchiveInfo(projectId, repoName)
            logger.info("Archive info: project $projectId, repo $repoName, num $num, size $size")
            metric.repoMetrics[repoName]?.let { repoMetric ->
                repoMetric.num.add(-num)
                repoMetric.size.add(-size)
            }
            metric.nodeNum.add(-num)
            metric.capSize.add(-size)
        }
    }


    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(14)
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is ProjectRepoMetricsStatJobContext)
        logger.info("start to insert [active: ${this.active}] project's metrics")
        for (entry in context.metrics) {
            storeMetrics(context.statDate, entry.value.toDO(active, context.statDate))
        }
        context.metrics.clear()
        logger.info("stat [active: ${this.active}] project metrics done")
    }

    override fun createJobContext(): ProjectRepoMetricsStatJobContext {
        val temp = mutableMapOf<String, Boolean>()
        activeProjectService.getActiveProjects().forEach {
            temp[it] = true
        }
        return ProjectRepoMetricsStatJobContext(
            statDate = LocalDate.now().atStartOfDay(),
            statProjects = temp
        )
    }

    private fun storeMetrics(
        statDate: LocalDateTime,
        projectMetric: TProjectMetrics,
    ) {
        if (projectMetric.capSize <= 0 && projectMetric.nodeNum <= 0) return
        // insert project repo metrics
        val criteria = Criteria.where(CREATED_DATE).isEqualTo(statDate).and(PROJECT).`is`(projectMetric.projectId)
        mongoTemplate.remove(Query(criteria), COLLECTION_NAME_PROJECT_METRICS)
        logger.info("stat project: [${projectMetric.projectId}], size: [${projectMetric.capSize}]")
        projectMetric.projectStatus = getProjectEnableStatus(projectMetric.projectId)
        mongoTemplate.insert(projectMetric, COLLECTION_NAME_PROJECT_METRICS)
    }

    private fun getProjectEnableStatus(projectId: String): Boolean? {
        val query = Query.query(Criteria.where(NAME).isEqualTo(projectId))
        val project = mongoTemplate.find(query, Project::class.java, COLLECTION_NAME_PROJECT)
            .firstOrNull() ?: return null
        return project.metadata.firstOrNull { it.key == "enabled" }?.value as Boolean?
    }

    /**
     * 获取归档信息
     * @return Pair(归档数,归档大小)
     * */
    private fun getArchiveInfo(projectId: String, repoName: String): Pair<Long, Long> {
        var num = 0L
        var size = 0L
        val query = Query(Criteria.where(NAME).isEqualTo(projectId))
        val project = mongoTemplate.find(query, Project::class.java, COLLECTION_NAME_PROJECT).firstOrNull()
            ?: return Pair(0, 0)
        val repoArchiveStatInfoStr = project.metadata.find { it.key == ARCHIVE_STAT_INFO }?.value?.toString()
            ?: return Pair(0, 0)
        try {
            val repoArchiveStatInfo =
                repoArchiveStatInfoStr.readJsonString<ConcurrentHashMap<String, RepoArchiveStatInfo>>()
            repoArchiveStatInfo[repoName]?.let {
                num += it.num
                size += it.size
            }
        } catch (e: Exception) {
            logger.error("get archive info error", e)
        }
        return Pair(num, size)
    }

    data class Project(var name: String, var metadata: List<ProjectMetadata> = emptyList()) {
        constructor(map: Map<String, Any?>) : this(
            map[Project::name.name].toString(),
            (map[Project::metadata.name] as? List<Map<String, Any>>)?.map {
                ProjectMetadata(it[ProjectMetadata::key.name].toString(), it[ProjectMetadata::value.name]!!)
            } ?: emptyList(),
        )
    }

    data class Repository(
        var projectId: String,
        var name: String,
        var type: String,
        var credentialsKey: String = "default",
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Repository::projectId.name].toString(),
            map[Repository::name.name].toString(),
            map[Repository::type.name].toString(),
            map[Repository::credentialsKey.name]?.toString() ?: "default",
        )
    }

    data class RepoArchiveStatInfo(
        val repoName: String,
        var size: Long,
        var num: Long,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectRepoMetricsStatJob::class.java)
        private const val COLLECTION_REPOSITORY_NAME = "repository"
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
        private const val COLLECTION_NAME_PROJECT = "project"
    }
}
