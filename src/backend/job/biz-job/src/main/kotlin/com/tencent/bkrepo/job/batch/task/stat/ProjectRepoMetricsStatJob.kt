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

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.BATCH_SIZE
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
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.config.properties.ProjectRepoMetricsStatJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 项目仓库指标统计任务
 */
open class ProjectRepoMetricsStatJob(
    val properties: ProjectRepoMetricsStatJobProperties,
    private val activeProjectService: ActiveProjectService,
    private val active: Boolean = true,
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
            val query = Query(
                Criteria.where(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(name)
                    .and(PATH).isEqualTo(PathUtils.ROOT).and(DELETED_DATE).isEqualTo(null),
            )
            val nodeCollectionName = COLLECTION_NODE_PREFIX +
                MongoShardingUtils.shardingSequence(projectId, SHARDING_COUNT)
            val key = FolderUtils.buildCacheKey(collectionName = nodeCollectionName, projectId = projectId)
            val metric = context.metrics.getOrPut(key) {
                ProjectRepoMetricsStatJobContext.ProjectMetrics(projectId)
            }
            val data = mongoTemplate.find<Node>(query, nodeCollectionName)
            if (data.isEmpty()) return
            data.forEach {
                runRepoMetrics(metric, row, it)
            }
            // 减掉指定项目归档大小
            if (properties.ignoreArchiveProjects.contains(projectId)) {
                val (num, size) = getArchiveInfo(projectId, name, nodeCollectionName)
                logger.info("Archive info: project $projectId, repo $name, num $num, size $size")
                metric.repoMetrics[name]!!.num.add((-num))
                metric.repoMetrics[name]!!.size.add(-size)
                metric.nodeNum.add(-num)
                metric.capSize.add(-size)
            }
        }
    }

    private fun runRepoMetrics(
        metric: ProjectRepoMetricsStatJobContext.ProjectMetrics,
        repo: Repository,
        node: Node,
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
        if (projectMetric.capSize <=0 && projectMetric.nodeNum <=0) return
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
    private fun getArchiveInfo(projectId: String, repoName: String, collectionName: String): Pair<Long, Long> {
        val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
            .and(ARCHIVED).isEqualTo(true)
            .and(DELETED_DATE).isEqualTo(null)
        var num = 0L
        var size = 0L
        val query = Query.query(criteria).cursorBatchSize(BATCH_SIZE)
        mongoTemplate.find(query, Node::class.java, collectionName).forEach {
            num++
            size += it.size
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

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val folder: Boolean,
        val fullPath: String,
        val size: Long,
        val nodeNum: Long? = null,
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Node::id.name].toString(),
            map[Node::projectId.name].toString(),
            map[Node::repoName.name].toString(),
            map[Node::folder.name] as Boolean,
            map[Node::fullPath.name].toString(),
            map[Node::size.name].toString().toLong(),
            map[Node::nodeNum.name]?.toString()?.toLong(),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectRepoMetricsStatJob::class.java)
        private const val COLLECTION_REPOSITORY_NAME = "repository"
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
        private const val COLLECTION_NAME_PROJECT = "project"
        private const val ARCHIVED = "archived"
    }
}
