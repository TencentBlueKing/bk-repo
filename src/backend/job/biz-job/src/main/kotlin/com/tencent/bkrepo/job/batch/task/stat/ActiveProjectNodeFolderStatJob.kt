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
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.NodeFolderJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.config.properties.ActiveProjectNodeFolderStatJobProperties
import com.tencent.bkrepo.job.pojo.FolderInfo
import org.bson.types.ObjectId
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.system.measureNanoTime

/**
 * 活跃项目下目录大小以及文件个数统计
 */
@Component
@EnableConfigurationProperties(ActiveProjectNodeFolderStatJobProperties::class)
class ActiveProjectNodeFolderStatJob(
    private val properties: ActiveProjectNodeFolderStatJobProperties,
    private val activeProjectService: ActiveProjectService,
    private val mongoTemplate: MongoTemplate,
    ): DefaultContextJob(properties) {

    override fun doStart0(jobContext: JobContext) {
        logger.info("start to do folder stat job for active projects")
        require(jobContext is NodeFolderJobContext)
        jobContext.activeProjects.forEach {
            val collectionName = COLLECTION_NAME_PREFIX +
                MongoShardingUtils.shardingSequence(it, SHARDING_COUNT)
            queryNodes(projectId = it, collection = collectionName, context = jobContext)
        }
        logger.info("folder stat job for active projects finished")
    }

    private fun queryNodes(
        projectId: String,
        collection: String,
        context: NodeFolderJobContext
    ){
        measureNanoTime {
            val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
                .and(DELETED_DATE).isEqualTo(null)
            if (!properties.runAllRepo && !specialRepoRunCheck() && properties.specialRepos.isNotEmpty()) {
                criteria.andOperator(Criteria().and(REPO).nin(properties.specialRepos))
            }
            val query = Query.query(criteria)
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val newQuery = Query.of(query)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(properties.batchSize)
                    .with(Sort.by(ID).ascending())
                val data = mongoTemplate.find<Node>(
                    newQuery,
                    collection,
                )
                if (data.isEmpty()) {
                    break
                }
                data.forEach { runRow(it, context) }
                querySize = data.size
                lastId = data.last().id as ObjectId
            } while (querySize == properties.batchSize)
        }.apply {
            val elapsedTime = HumanReadable.time(this)
            // 当表执行完成后，将属于该表的所有记录写入数据库
            storeMemoryCacheToDB(projectId, collection, context)
            logger.info("project $projectId run completed, elapse $elapsedTime")
        }
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

    private fun isSpecialRepo(repoName: String): Boolean {
        return properties.specialRepos.contains(repoName)
    }

    fun runRow(row: Node, context: NodeFolderJobContext) {
        //只统计非目录类节点；没有根目录这个节点，不需要统计
        if (row.path == PathUtils.ROOT) {
            return
        }

        // 更新当前节点所有上级目录（排除根目录）统计信息
        val folderFullPaths = PathUtils.resolveAncestorFolder(row.fullPath)
        for (fullPath in folderFullPaths) {
            if (fullPath == PathUtils.ROOT) continue
            updateMemoryCache(
                projectId = row.projectId,
                repoName = row.repoName,
                fullPath = fullPath,
                size = row.size,
                context = context
            )
        }
    }

    override fun createJobContext(): NodeFolderJobContext {
        return NodeFolderJobContext(
            activeProjects = activeProjectService.getActiveProjects()
        )
    }


    /**
     * 更新内存缓存中对应key下将新增的size和nodeNum
     */
    private fun updateMemoryCache(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        context: NodeFolderJobContext
    ) {
        val key = FolderUtils.buildCacheKey(projectId = projectId, repoName = repoName, fullPath = fullPath)
        val folderMetrics = context.folderCache.getOrPut(key) { NodeFolderJobContext.FolderMetrics() }
        folderMetrics.capSize.add(size)
        folderMetrics.nodeNum.increment()
    }

    /**
     * 将memory缓存中属于projectId下的记录写入DB中
     */
    private fun storeMemoryCacheToDB(
        projectId: String,
        collectionName: String,
        context: NodeFolderJobContext
    ) {
        logger.info("store memory cache to db withe projectId $projectId")
        if (context.folderCache.isEmpty()) {
            return
        }
        val updateList = ArrayList<org.springframework.data.util.Pair<Query, Update>>()
        for(entry in context.folderCache) {
            extractFolderInfoFromCacheKey(entry.key)?.let {
                updateList.add(buildUpdateClausesForFolder(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    size = entry.value.capSize.toLong(),
                    nodeNum = entry.value.nodeNum.toLong()
                ))
            }
            if (updateList.size >= BATCH_LIMIT) {
                mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                    .updateOne(updateList)
                    .execute()
                updateList.clear()
            }
        }
        if (updateList.isEmpty()) return
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
            .updateOne(updateList)
            .execute()
        updateList.clear()
        context.folderCache.clear()
    }

    private fun extractFolderInfoFromCacheKey(key: String): FolderInfo? {
        val values = key.split(StringPool.COLON)
        return try {
            FolderInfo(
                projectId = values[0],
                repoName = values[1],
                fullPath = values[2]
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
        val folder: Boolean

        @JvmField
        val path: String

        @JvmField
        val fullPath: String

        @JvmField
        val name: String

        @JvmField
        val size: Long

        @JvmField
        val projectId: String

        @JvmField
        val repoName: String

        init {
            id = map[Node::id.name] as String
            folder = map[Node::folder.name] as Boolean
            path = map[Node::path.name] as String
            fullPath = map[Node::fullPath.name] as String
            name = map[Node::name.name] as String
            size = map[Node::size.name].toString().toLong()
            projectId = map[Node::projectId.name] as String
            repoName = map[Node::repoName.name] as String
        }
    }


    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private const val BATCH_LIMIT = 500
        private const val COLLECTION_NAME_PREFIX = "node_"
    }

}
