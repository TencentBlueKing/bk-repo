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

package com.tencent.bkrepo.job.batch.task.stat

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.IGNORE_PROJECT_PREFIX_LIST
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.NodeFolderJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils.buildCacheKey
import com.tencent.bkrepo.job.config.properties.InactiveProjectNodeFolderStatJobProperties
import com.tencent.bkrepo.job.pojo.FolderInfo
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.BulkOperations.BulkMode
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis


/**
 * 非活跃项目下目录大小以及文件个数统计
 */
@Component
@EnableConfigurationProperties(InactiveProjectNodeFolderStatJobProperties::class)
class InactiveProjectNodeFolderStatJob(
    private val properties: InactiveProjectNodeFolderStatJobProperties,
    private val activeProjectService: ActiveProjectService
) : DefaultContextMongoDbJob<InactiveProjectNodeFolderStatJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.toList()
    }

    override fun buildQuery(): Query {
        var criteria = Criteria.where(DELETED_DATE).`is`(null)
            .and(FOLDER).`is`(false)
        if (!properties.runAllRepo && specialRepoRunCheck() && properties.specialRepos.isNotEmpty()) {
            criteria = criteria.and(REPO).nin(properties.specialRepos)
        }
        return Query(criteria)
    }

    override fun mapToEntity(row: Map<String, Any?>): Node = Node(row)

    override fun entityClass(): KClass<Node> = Node::class

    /**
     * 最长加锁时间
     */
    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    fun statProjectCheck(
        projectId: String,
        context: NodeFolderJobContext
    ): Boolean {
        return context.activeProjects[projectId] != null
    }

    // 特殊仓库每周统计一次
    private fun specialRepoRunCheck(): Boolean {
        val runDay = if (properties.specialDay < 1 || properties.specialDay > 7) {
            6
        } else {
            properties.specialDay
        }
        return DayOfWeek.of(runDay) == LocalDateTime.now().dayOfWeek
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        require(context is NodeFolderJobContext)
        if (statProjectCheck(row.projectId, context)) return
        //只统计非目录类节点；没有根目录这个节点，不需要统计
        if (row.path == PathUtils.ROOT) {
            return
        }
        // 判断是否在不统计项目或者仓库列表中
        if (ignoreProjectOrRepoCheck(row.projectId)) return

        // 更新当前节点所有上级目录（排除根目录）统计信息
        val folderFullPaths = PathUtils.resolveAncestorFolder(row.fullPath)
        for (fullPath in folderFullPaths) {
            if (fullPath == PathUtils.ROOT) continue
            updateCache(
                collectionName = collectionName,
                projectId = row.projectId,
                repoName = row.repoName,
                fullPath = fullPath,
                size = row.size,
                context = context
            )
        }
    }

    override fun createJobContext(): NodeFolderJobContext {
        val temp = mutableMapOf<String, Boolean>()
        activeProjectService.getActiveProjects().forEach {
            temp[it] = true
        }
        return NodeFolderJobContext(
            activeProjects = temp
        )
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is NodeFolderJobContext)
        // 当表执行完成后，将属于该表的所有记录写入数据库
        storeMemoryCacheToDB(collectionName, context)
        context.projectMap.remove(collectionName)
    }

    /**
     * 判断项目或者仓库是否不需要进行目录统计
     */
    private fun ignoreProjectOrRepoCheck(projectId: String): Boolean {
        return IGNORE_PROJECT_PREFIX_LIST.firstOrNull { projectId.startsWith(it) } != null
    }

    /**
     * 更新缓存中的size和nodeNum
     */
    private fun updateCache(
        collectionName: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        context: NodeFolderJobContext
    ) {
        val elapsedTime = measureTimeMillis {
            updateMemoryCache(
                collectionName = collectionName,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                size = size,
                context = context
            )
            context.projectMap.putIfAbsent(collectionName, mutableSetOf())
            context.projectMap[collectionName]!!.add(projectId)
        }
        logger.debug("updateCache, elapse: $elapsedTime")
    }

    /**
     * 更新内存缓存中对应key下将新增的size和nodeNum
     */
    private fun updateMemoryCache(
        collectionName: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        context: NodeFolderJobContext
    ) {
        val key = buildCacheKey(
            collectionName = collectionName, projectId = projectId, repoName = repoName, fullPath = fullPath
        )
        val folderMetrics = context.folderCache.getOrPut(key) { NodeFolderJobContext.FolderMetrics() }
        folderMetrics.capSize.add(size)
        folderMetrics.nodeNum.increment()
    }

    /**
     * 将memory缓存中属于collectionName下的记录写入DB中
     */
    private fun storeMemoryCacheToDB(collectionName: String, context: NodeFolderJobContext) {
        logger.info("store memory cache to db withe table $collectionName")
        if (context.folderCache.isEmpty()) {
            return
        }
        val updateList = ArrayList<org.springframework.data.util.Pair<Query, Update>>()
        val prefix = buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        val storedKeys = mutableSetOf<String>()
        for (entry in context.folderCache) {
            if (!entry.key.startsWith(prefix)) continue
            extractFolderInfoFromCacheKey(entry.key).let {
                storedKeys.add(entry.key)
                updateList.add(
                    buildUpdateClausesForFolder(
                        projectId = it!!.projectId,
                        repoName = it.repoName,
                        fullPath = it.fullPath,
                        size = entry.value.capSize.toLong(),
                        nodeNum = entry.value.nodeNum.toLong()
                    )
                )
            }
            if (updateList.size >= BATCH_LIMIT) {
                mongoTemplate.bulkOps(BulkMode.UNORDERED, collectionName)
                    .updateOne(updateList)
                    .execute()
                updateList.clear()
            }
        }
        if (updateList.isEmpty()) return
        mongoTemplate.bulkOps(BulkMode.UNORDERED, collectionName)
            .updateOne(updateList)
            .execute()
        updateList.clear()
        for (key in storedKeys) {
            context.folderCache.remove(key)
        }
    }

    /**
     * 从缓存key中解析出节点信息
     */
    fun extractFolderInfoFromCacheKey(key: String): FolderInfo? {
        val values = key.split(StringPool.COLON)
        return try {
            FolderInfo(
                projectId = values[1],
                repoName = values[2],
                fullPath = values[3]
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 生成db更新语句
     */
    private fun buildUpdateClausesForFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ): org.springframework.data.util.Pair<Query, Update> {
        val query = Query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
                .and(FULL_PATH).isEqualTo(fullPath)
                .and(DELETED_DATE).isEqualTo(null)
                .and(FOLDER).isEqualTo(true)
        )
        val update = Update().set(SIZE, size)
            .set(NODE_NUM, nodeNum)
        return org.springframework.data.util.Pair.of(query, update)
    }

    data class Node(private val map: Map<String, Any?>) {
        // 需要通过@JvmField注解将Kotlin backing-field直接作为Java field使用，MongoDbBatchJob中才能解析出需要查询的字段
        @JvmField
        val id: String

        @JvmField
        val path: String

        @JvmField
        val fullPath: String

        @JvmField
        val size: Long

        @JvmField
        val projectId: String

        @JvmField
        val repoName: String

        init {
            id = map[Node::id.name] as String
            path = map[Node::path.name] as String
            fullPath = map[Node::fullPath.name] as String
            size = map[Node::size.name].toString().toLong()
            projectId = map[Node::projectId.name] as String
            repoName = map[Node::repoName.name] as String
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InactiveProjectNodeFolderStatJob::class.java)
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private const val BATCH_LIMIT = 500
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
