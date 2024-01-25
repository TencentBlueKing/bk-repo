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
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.IGNORE_PROJECT_PREFIX_LIST
import com.tencent.bkrepo.job.MEMORY_CACHE_TYPE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REDIS_CACHE_TYPE
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.FolderChildContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils.buildCacheKey
import com.tencent.bkrepo.job.batch.utils.FolderUtils.extractFolderInfoFromCacheKey
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import com.tencent.bkrepo.job.config.properties.NodeStatCompositeMongoDbBatchJobProperties
import com.tencent.bkrepo.job.pojo.FolderInfo
import org.springframework.data.mongodb.core.BulkOperations.BulkMode
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis
import kotlin.text.toLongOrNull as toLongOrNull1


/**
 * 目录大小以及文件个数统计
 */
class FolderStatChildJob(
    val properties: CompositeJobProperties,
    private val redisTemplate: RedisTemplate<String, String>,
    private val mongoTemplate: MongoTemplate,
    private val activeProjectService: ActiveProjectService
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>() {

    override fun onParentJobStart(context: ChildJobContext) {
        require(context is FolderChildContext)
        runTaskCheck(context)
        logger.info("start to stat the size of folder, run flag is ${context.runFlag}")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is FolderChildContext)
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        if (!context.runFlag) return
        if (!collectionRunCheck(collectionName)) return
        if (row.deleted != null) return
        //只统计非目录类节点；没有根目录这个节点，不需要统计
        if (row.folder || row.path == PathUtils.ROOT) {
            return
        }
        // 判断是否在不统计项目或者仓库列表中
        if (ignoreProjectOrRepoCheck(row.projectId)) return
        if (context.activeProjects.isNotEmpty() && !properties.runAllProjects &&
            !context.activeProjects.contains(row.projectId)) return

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

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is FolderChildContext)
        logger.info("stat size of folder done")
    }


    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        val cacheType = try {
            redisTemplate.execute { null }
            REDIS_CACHE_TYPE
        } catch (e: Exception) {
            MEMORY_CACHE_TYPE
        }
        return FolderChildContext(
            parentContent = parentJobContext,
            cacheType = cacheType,
            activeProjects = if (properties.runAllProjects) { emptySet() }
            else { activeProjectService.getActiveProjects() },
        )
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is FolderChildContext)
        // 如果使用的是redis作为缓存，将内存中的临时记录写入redis
        updateRedisCache(context, force = true, collectionName = collectionName)
        // 当表执行完成后，将属于该表的所有记录写入数据库
        storeCacheToDB(collectionName, context)
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
        context: FolderChildContext
    ) {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        val elapsedTime = measureTimeMillis {
            if (context.cacheType == REDIS_CACHE_TYPE && collectionName in properties.redisCacheCollections) {
                updateRedisCache(
                    collectionName = collectionName,
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    size = size,
                    context = context
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
            context.projectMap.putIfAbsent(collectionName, mutableSetOf())
            context.projectMap[collectionName]!!.add(projectId)
        }
        logger.debug("updateCache, elapse: $elapsedTime")
    }

    /**
     * 更新redis缓存中对应key下将新增的size和nodeNum
     */
    private fun updateRedisCache(
        collectionName: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        context: FolderChildContext
    ) {
        // 避免每次请求都去请求redis， 先将数据缓存在本地cache中，到达上限后更新到redis
        updateMemoryCache(
            collectionName = collectionName,
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            size = size,
            context = context
        )
        updateRedisCache(context, collectionName = collectionName)
    }

    /**
     * 将存储在内存中的临时记录更新到redis
     */
    private fun updateRedisCache(
        context: FolderChildContext,
        force: Boolean = false,
        collectionName: String
    ) {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        if (context.cacheType != REDIS_CACHE_TYPE) return
        if (collectionName !in properties.redisCacheCollections) return
        if (!force && context.folderCache.size < 50000) return
        if (context.folderCache.isEmpty()) return

        // 避免每次设置值都创建一个 Redis 连接
        redisTemplate.execute { connection ->
            val hashCommands = connection.hashCommands()
            for (entry in context.folderCache) {
                val folderInfo = extractFolderInfoFromCacheKey(entry.key) ?: continue
                val cName = extractCollectionNameFromCacheKey(entry.key)
                if (!cName.isNullOrEmpty()) {
                    val sizeHKey = buildCacheKey(
                        projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                        fullPath = folderInfo.fullPath, tag = SIZE
                    )
                    val nodeNumHKey = buildCacheKey(
                        projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                        fullPath = folderInfo.fullPath, tag = NODE_NUM
                    )
                    val key = buildCacheKey(collectionName = cName, projectId = folderInfo.projectId)
                    hashCommands.hIncrBy(key.toByteArray(), sizeHKey.toByteArray(), entry.value.capSize.toLong())
                    hashCommands.hIncrBy(key.toByteArray(), nodeNumHKey.toByteArray(), entry.value.nodeNum.toLong())
                }
            }
            null
        }
        context.folderCache.clear()
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
        val key = buildCacheKey(
            collectionName = collectionName, projectId = projectId, repoName = repoName, fullPath = fullPath
        )
        val folderMetrics = context.folderCache.getOrPut(key) { FolderChildContext.FolderMetrics() }
        folderMetrics.capSize.add(size)
        folderMetrics.nodeNum.increment()
    }

    private fun runTaskCheck(context: FolderChildContext) {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        // 当值小于 1 时，任务不执行
        if (properties.runPolicy <= 0) return
        context.runFlag = true
    }

    /**
     * 判断该collectionName是否允许执行
     */
    private fun collectionRunCheck(collectionName: String): Boolean {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        if (!properties.multipleExecutions) {
            return true
        }
        val collectionNum = collectionName.removePrefix(COLLECTION_NAME_PREFIX).toIntOrNull() ?: return false
        val remainder = collectionNum % 7 + 1

        // 当值为1 - 7时，优先执行node_num%7 +1 == runPolicy对应的node表
        if (properties.runPolicy in 1..7) {
            return properties.runPolicy == remainder
        }
        return DayOfWeek.of(remainder) == LocalDateTime.now().dayOfWeek
    }

    /**
     * 将缓存中的数据更新到DB中
     */
    private fun storeCacheToDB(collectionName: String, context: FolderChildContext) {
        require(properties is NodeStatCompositeMongoDbBatchJobProperties)
        if (context.cacheType == REDIS_CACHE_TYPE && collectionName in properties.redisCacheCollections) {
            storeRedisCacheToDB(collectionName, context)
        } else {
            storeMemoryCacheToDB(collectionName, context)
        }
    }


    /**
     * 将redis缓存中属于collectionName下的记录写入DB中
     */
    private fun storeRedisCacheToDB(collectionName: String, context: FolderChildContext) {
        logger.info("store redis cache to db withe table $collectionName")
        val hashOps = redisTemplate.opsForHash<String, String>()
        context.projectMap[collectionName]?.forEach {
            storeFolderOfProject(collectionName, it, hashOps)
        }
    }


    /**
     * 存储对应项目下缓存在redis下的folder记录
     */
    private fun storeFolderOfProject(
        collectionName: String,
        projectId: String,
        hashOps: HashOperations<String, String, String>
    ) {
        val projectKey = buildCacheKey(collectionName = collectionName, projectId = projectId)
        val storedProjectIdKey = buildCacheKey(collectionName = collectionName, projectId = projectId, tag = STORED)
        val updateList = ArrayList<org.springframework.data.util.Pair<Query, Update>>()

        val options = ScanOptions.scanOptions().build()
        redisTemplate.execute { connection ->
            val hashCommands = connection.hashCommands()
            val cursor = hashCommands.hScan(projectKey.toByteArray(), options)
            while (cursor.hasNext()) {
                val entry: Map.Entry<ByteArray, ByteArray> = cursor.next()
                val folderInfo = extractFolderInfoFromRedisKey(String(entry.key)) ?: continue
                // 由于可能KEYS或者SCAN命令会被禁用，调整redis存储格式，key为collectionName,
                // hkey为projectId:repoName:fullPath:size或者nodenum, hvalue为对应值,
                // 为了避免遍历时删除，用一个额外的key去记录当前collectionName+project下已经存储到db的目录记录
                val storedFolderHkey = buildCacheKey(
                    projectId = folderInfo.projectId, repoName = folderInfo.repoName, fullPath = folderInfo.fullPath
                )
                val storedHkeyExist = hashCommands.hExists(
                    storedProjectIdKey.toByteArray(), storedFolderHkey.toByteArray()
                )
                if (storedHkeyExist == null || !storedHkeyExist) {
                    val statInfo = getFolderStatInfo(
                        collectionName, entry, folderInfo, hashOps
                    )
                    updateList.add(buildUpdateClausesForFolder(
                        projectId = folderInfo.projectId,
                        repoName = folderInfo.repoName,
                        fullPath = folderInfo.fullPath,
                        size = statInfo.size,
                        nodeNum = statInfo.nodeNum
                    ))
                    if (updateList.size >= BATCH_LIMIT) {
                        mongoTemplate.bulkOps(BulkMode.UNORDERED,collectionName)
                            .updateOne(updateList)
                            .execute()
                        updateList.clear()
                    }
                    hashCommands.hSet(
                        storedProjectIdKey.toByteArray(), storedFolderHkey.toByteArray(), STORED.toByteArray()
                    )
                }
            }
        }
        if (updateList.isNotEmpty()) {
            mongoTemplate.bulkOps(BulkMode.UNORDERED,collectionName)
                .updateOne(updateList)
                .execute()
            updateList.clear()
        }
        redisTemplate.delete(projectKey)
        redisTemplate.delete(storedProjectIdKey)
    }


    /**
     * 从redis中获取对应目录的统计信息
     */
    private fun getFolderStatInfo(
        collectionName: String,
        entry: Map.Entry<ByteArray, ByteArray>,
        folderInfo: FolderInfo,
        hashOps: HashOperations<String, String, String>
    ): StatInfo {
        val size: Long
        val nodeNum: Long
        val key = buildCacheKey(collectionName = collectionName, projectId = folderInfo.projectId)
        if (String(entry.key).endsWith(SIZE)) {
            val nodeNumKey = buildCacheKey(
                projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                fullPath = folderInfo.fullPath, tag = NODE_NUM
            )
            size = String(entry.value).toLongOrNull1() ?: 0
            nodeNum = hashOps.get(key, nodeNumKey)?.toLongOrNull1() ?: 0
        } else {
            val sizeKey = buildCacheKey(
                projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                fullPath = folderInfo.fullPath, tag = SIZE
            )
            nodeNum = String(entry.value).toLongOrNull1() ?: 0
            size = hashOps.get(key, sizeKey)?.toLongOrNull1() ?: 0
        }
        return StatInfo(size, nodeNum)
    }

    /**
     * 将memory缓存中属于collectionName下的记录写入DB中
     */
    private fun storeMemoryCacheToDB(collectionName: String, context: FolderChildContext) {
        logger.info("store memory cache to db withe table $collectionName")
        if (context.folderCache.isEmpty()) {
            return
        }
        val updateList = ArrayList<org.springframework.data.util.Pair<Query, Update>>()
        val prefix = buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        val storedKeys = mutableSetOf<String>()
        for(entry in context.folderCache) {
            if (!entry.key.startsWith(prefix)) continue
            extractFolderInfoFromCacheKey(entry.key)?.let {
                storedKeys.add(entry.key)
                updateList.add(buildUpdateClausesForFolder(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    size = entry.value.capSize.toLong(),
                    nodeNum = entry.value.nodeNum.toLong()
                ))
            }
            if (updateList.size >= BATCH_LIMIT) {
                mongoTemplate.bulkOps(BulkMode.UNORDERED,collectionName)
                    .updateOne(updateList)
                    .execute()
                updateList.clear()
            }
        }
        if (updateList.isEmpty()) return
        mongoTemplate.bulkOps(BulkMode.UNORDERED,collectionName)
            .updateOne(updateList)
            .execute()
        updateList.clear()
        for (key in storedKeys) {
            context.folderCache.remove(key)
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


    private fun extractFolderInfoFromRedisKey(key: String): FolderInfo? {
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
     * 从缓存key中解析出collectionName
     */
    private fun extractCollectionNameFromCacheKey(key: String): String? {
        val values = key.split(StringPool.COLON)
        return try {
            values.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    data class StatInfo(
        var size: Long,
        var nodeNum: Long
    )


    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private const val STORED = "stored"
        private const val BATCH_LIMIT = 500
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
