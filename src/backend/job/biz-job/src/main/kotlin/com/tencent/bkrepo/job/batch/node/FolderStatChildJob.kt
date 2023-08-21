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

package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULLPATH
import com.tencent.bkrepo.job.MEMORY_CACHE_TYPE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REDIS_CACHE_TYPE
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.FolderChildContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils.shardingSequence
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import com.tencent.bkrepo.job.config.properties.NodeStatCompositeMongoDbBatchJobProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.redis.core.RedisTemplate
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.text.toLongOrNull as toLongOrNull1


/**
 * 目录大小以及文件个数统计
 */
class FolderStatChildJob(
    val properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    private val lock: ReentrantLock = ReentrantLock()


    override fun onParentJobStart(context: ChildJobContext) {
        require(context is FolderChildContext)
        initCheck(context)
        logger.info("start to stat the size of folder, init flag is ${context.initFlag}")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is FolderChildContext)
        if (context.initFlag) return
        if (row.deleted != null) return
        // 判断是否在不统计项目或者仓库列表中
        if (ignoreProjectOrRepoCheck(row.projectId, row.repoName)) return
        //只统计非目录类节点；没有根目录这个节点，不需要统计
        if (row.folder || row.path == PathUtils.ROOT) {
            return
        }

        // 更新当前节点所有上级目录（排除根目录）统计信息
        PathUtils.resolveAncestorFolder(row.fullPath).forEach {
            if (it != PathUtils.ROOT) {
                updateCache(
                    collectionName = collectionName,
                    projectId = row.projectId,
                    repoName = row.repoName,
                    fullPath = it,
                    size = row.size,
                    context = context
                )
            }
        }
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is FolderChildContext)
        logger.info("stat size of folder done")
    }


    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        val cacheType = try {
            redisTemplate.execute { null }
            REDIS_CACHE_TYPE
        } catch (e: Exception) {
            MEMORY_CACHE_TYPE
        }
        return FolderChildContext(parentJobContext, cacheType = cacheType)
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is FolderChildContext)
        // 当表执行完成后，将属于该表的所有记录写入数据库
        storeCacheToDB(collectionName, context)
    }

    /**
     * 判断项目或者仓库是否不需要进行目录统计
     */
    private fun ignoreProjectOrRepoCheck(projectId: String, repoName: String): Boolean {
        IGNORE_PROJECT_PREFIX_LIST.forEach {
            if (projectId.startsWith(it)){
                return true
            }
        }
        return IGNORE_REPO_LIST.contains(repoName)
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
        context: FolderChildContext
    ) {
        if (context.cacheType == REDIS_CACHE_TYPE) {
            updateRedisCache(
                collectionName = collectionName,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                size = size
            )
        } else {
            updateMemoryCache(
                collectionName = collectionName,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                size = size,
                context = context
            )
        }
    }

    /**
     * 更新redis缓存中对应key下将新增的size和nodeNum
     */
    private fun updateRedisCache(
        collectionName: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long
    ) {
        val key = buildCacheKey(collectionName, projectId, repoName, fullPath)
        val hashOps = redisTemplate.opsForHash<String, Long>()
        hashOps.increment(key, SIZE, size)
        hashOps.increment(key, NODE_NUM, 1)
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
        context: FolderChildContext
    ) {
        val key = buildCacheKey(collectionName, projectId, repoName, fullPath)
        val folderMetrics = context.folderCache.getOrPut(key) { FolderChildContext.FolderMetrics() }
        folderMetrics.capSize.add(size)
        folderMetrics.nodeNum.increment()
    }

    private fun initCheck(context: FolderChildContext) {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        if (LocalDateTime.now().dayOfWeek != DayOfWeek.of(properties.dayOfWeek)) {
            return
        }
        context.initFlag = false
    }

    /**
     * 将缓存中的数据更新到DB中
     */
    private fun storeCacheToDB(collectionName: String, context: FolderChildContext) {
        if (context.cacheType == REDIS_CACHE_TYPE) {
            storeRedisCacheToDB(collectionName, context)
        } else {
            storeMemoryCacheToDB(collectionName, context)
        }
    }


    /**
     * 将redis缓存中属于collectionName下的记录写入DB中
     */
    private fun storeRedisCacheToDB(collectionName: String, context: FolderChildContext) {
        val prefix = buildCacheKeyPrefix(collectionName)
        val hashOps = redisTemplate.opsForHash<String, String>()
        redisTemplate.keys("$prefix${StringPool.POUND}").forEach { key ->
            extractFolderInfo(key)?.let {
                setSizeAndNodeNumOfFolder(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    size = hashOps.get(key, SIZE)?.toLongOrNull1() ?: 0,
                    nodeNum = hashOps.get(key, NODE_NUM)?.toLongOrNull1() ?: 0
                )
            }
            redisTemplate.delete(key)
        }
    }

    /**
     * 将memory缓存中属于collectionName下的记录写入DB中
     */
    private fun storeMemoryCacheToDB(collectionName: String, context: FolderChildContext) {
        if (context.folderCache.isEmpty()) {
            return
        }
        val prefix = buildCacheKeyPrefix(collectionName)
        context.folderCache.filterKeys { it.startsWith(prefix) }.forEach {  entry ->
            extractFolderInfo(entry.key)?.let {
                setSizeAndNodeNumOfFolder(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    size = entry.value.capSize.toLong(),
                    nodeNum = entry.value.nodeNum.toLong()
                )
            }
        }
    }

    /**
     * 更新目录对应size和nodeNum
     */
    private fun setSizeAndNodeNumOfFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ) {
        val query = Query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
                .and(FULLPATH).isEqualTo(fullPath)
                .and(DELETED_DATE).isEqualTo(null)
                .and(FOLDER).isEqualTo(true)
        )
        val update = Update().set(SIZE, size)
            .set(NODE_NUM, nodeNum)
        val nodeCollectionName = COLLECTION_NODE_PREFIX + shardingSequence(projectId, SHARDING_COUNT)
        mongoTemplate.updateFirst(query, update, nodeCollectionName)
    }


    /**
     * 生成缓存key
     */
    private fun buildCacheKey(
        collectionName: String,
        projectId: String,
        repoName: String,
        fullPath: String
    ): String {
        return StringBuilder().append(buildCacheKeyPrefix(collectionName))
            .append(projectId).append(StringPool.SLASH).append(repoName)
            .append(fullPath).toString()
    }

    /**
     * 生成缓存key前缀
     */
    private fun buildCacheKeyPrefix(collectionName: String): String {
        return StringBuilder().append(collectionName).append(StringPool.SLASH).toString()
    }

    private fun extractFolderInfo(key: String): FolderInfo? {
        val values = key.split(StringPool.SLASH)
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



    data class FolderInfo(
        var projectId: String,
        var repoName: String,
        var fullPath: String
    )


    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private val IGNORE_PROJECT_PREFIX_LIST = listOf("CODE_", "CLOSED_SOURCE_", "git_")
        private val IGNORE_REPO_LIST = listOf(REPORT, LOG)
    }
}
