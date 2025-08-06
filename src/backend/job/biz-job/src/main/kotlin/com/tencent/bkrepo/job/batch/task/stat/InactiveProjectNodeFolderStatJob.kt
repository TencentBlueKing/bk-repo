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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.IGNORE_PROJECT_PREFIX_LIST
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.NodeFolderJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.StatUtils.specialRepoRunCheck
import com.tencent.bkrepo.job.config.properties.InactiveProjectNodeFolderStatJobProperties
import com.tencent.bkrepo.job.pojo.stat.StatNode
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass


/**
 * 非活跃项目下目录大小以及文件个数统计
 */
@Component
@EnableConfigurationProperties(InactiveProjectNodeFolderStatJobProperties::class)
class InactiveProjectNodeFolderStatJob(
    private val properties: InactiveProjectNodeFolderStatJobProperties,
    private val activeProjectService: ActiveProjectService,
    private val nodeFolderStat: NodeFolderStat,
) : DefaultContextMongoDbJob<StatNode>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.toList()
    }

    override fun buildQuery(): Query {
        return Query(buildCriteria())
    }

    private fun buildCriteria(): Criteria {
        var criteria = Criteria.where(DELETED_DATE).`is`(null)
            .and(FOLDER).`is`(false)
        if (
            !properties.runAllRepo && !specialRepoRunCheck(properties.specialDay)
            && properties.specialRepos.isNotEmpty()
        ) {
            criteria = criteria.and(REPO).nin(properties.specialRepos)
        }
        return criteria
    }

    override fun mapToEntity(row: Map<String, Any?>): StatNode = StatNode(row)

    override fun entityClass(): KClass<StatNode> = StatNode::class

    /**
     * 最长加锁时间
     */
    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    fun statProjectCheck(
        projectId: String,
        context: NodeFolderJobContext,
    ): Boolean {
        return context.activeProjects[projectId] != null
    }

    override fun run(row: StatNode, collectionName: String, context: JobContext) {
        require(context is NodeFolderJobContext)
        if (statProjectCheck(row.projectId, context)) return
        // 判断是否在不统计项目或者仓库列表中
        if (ignoreProjectOrRepoCheck(row.projectId)) return
        val node = nodeFolderStat.buildNode(
            id = row.id,
            projectId = row.projectId,
            repoName = row.repoName,
            path = row.path,
            fullPath = row.fullPath,
            folder = row.folder,
            size = row.size
        )
        nodeFolderStat.collectNode(
            node = node,
            context = context,
            useMemory = properties.userMemory,
            keyPrefix = KEY_PREFIX,
            collectionName = collectionName,
            cacheNumLimit = properties.cacheNumLimit
        )
    }

    override fun createJobContext(): NodeFolderJobContext {
        beforeRunCollection()
        val temp = mutableMapOf<String, Boolean>()
        activeProjectService.getActiveProjects().forEach {
            temp[it] = true
        }
        return NodeFolderJobContext(
            activeProjects = temp,
            separationProjects = nodeFolderStat.buildSeparationProjectList()
        )
    }

    private fun beforeRunCollection() {
        if (properties.userMemory) return
        // 每次任务启动前要将redis上对应的key清理， 避免干扰
        collectionNames().forEach {
            val key = KEY_PREFIX + StringPool.COLON + FolderUtils.buildCacheKey(
                collectionName = it, projectId = StringPool.EMPTY
            )
            nodeFolderStat.removeRedisKey(key)
        }
    }

    override fun onRunCollectionStart(collectionName: String, context: JobContext) {
        require(context is NodeFolderJobContext)
        context.separationProjects[collectionName]?.let { projectSet ->
            projectSet.filter { context.activeProjects[it] == null }.forEach { projectId ->
                logger.info("Processing inactive projectId: $projectId in collection: $collectionName")
                nodeFolderStat.processSeparationQuery(
                    projectId = projectId,
                    criteria = buildCriteria(),
                    batchSize = properties.batchSize,
                    shouldRun = shouldRun(),
                    processor = { node -> run(node, collectionName, context) }
                )
            }
        }
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is NodeFolderJobContext)
        // 当表执行完成后，将属于该表的所有记录写入数据库
        logger.info("store memory cache to db withe table $collectionName")
        if (!properties.userMemory) {
            nodeFolderStat.updateRedisCache(
                context = context,
                force = true,
                keyPrefix = KEY_PREFIX,
                collectionName = collectionName,
                cacheNumLimit = properties.cacheNumLimit
            )
        }

        if (properties.userMemory) {
            logger.info("store memory cache to db withe table $collectionName")
            nodeFolderStat.storeMemoryCacheToDB(context, collectionName, runCollection = true)
        } else {
            logger.info("store redis cache to db withe table $collectionName")
            nodeFolderStat.storeRedisCacheToDB(context, KEY_PREFIX, collectionName, runCollection = true)
        }
    }

    /**
     * 判断项目或者仓库是否不需要进行目录统计
     */
    private fun ignoreProjectOrRepoCheck(projectId: String): Boolean {
        return IGNORE_PROJECT_PREFIX_LIST.firstOrNull { projectId.startsWith(it) } != null
    }


    companion object {
        private val logger = LoggerFactory.getLogger(InactiveProjectNodeFolderStatJob::class.java)
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val KEY_PREFIX = "inactiveProjectNode"

    }
}
