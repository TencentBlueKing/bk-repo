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
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.batch.context.NodeFolderJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.FolderUtils.extractFolderInfoFromCacheKey
import com.tencent.bkrepo.job.pojo.FolderInfo
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Component

@Component
class NodeFolderStat(
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
) {

    fun buildNode(
        id: String,
        folder: Boolean,
        path: String,
        fullPath: String,
        size: Long,
        projectId: String,
        repoName: String,
    ): Node {
        return Node(
            id = id,
            projectId = projectId,
            repoName = repoName,
            path = path,
            fullPath = fullPath,
            folder = folder,
            size = size
        )
    }

    fun collectNode(
        node: Node,
        context: NodeFolderJobContext,
        useMemory: Boolean,
        keyPrefix: String,
        collectionName: String? = null,
    ) {
        //只统计非目录类节点；没有根目录这个节点，不需要统计
        if (node.path == PathUtils.ROOT) {
            return
        }
        // 更新当前节点所有上级目录（排除根目录）统计信息
        val folderFullPaths = PathUtils.resolveAncestorFolder(node.fullPath)
        for (fullPath in folderFullPaths) {
            if (fullPath == PathUtils.ROOT) continue
            updateMemoryCache(
                projectId = node.projectId,
                repoName = node.repoName,
                fullPath = fullPath,
                size = node.size,
                context = context,
                collectionName = collectionName
            )
            if (!useMemory) {
                // 避免每次请求都去请求redis， 先将数据缓存在本地cache中，到达上限后更新到redis
                updateRedisCache(
                    context = context,
                    collectionName = collectionName,
                    keyPrefix = keyPrefix,
                    projectId = node.projectId
                )
            }
        }
    }

    /**
     * 更新内存缓存中对应key下将新增的size和nodeNum
     */
    private fun updateMemoryCache(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        context: NodeFolderJobContext,
        collectionName: String? = null
    ) {
        val key = FolderUtils.buildCacheKey(
            collectionName = collectionName, projectId = projectId, repoName = repoName, fullPath = fullPath
        )
        val folderMetrics = context.folderCache.getOrPut(key) { NodeFolderJobContext.FolderMetrics() }
        folderMetrics.capSize.add(size)
        folderMetrics.nodeNum.increment()
    }

    /**
     * 将存储在内存中的临时记录更新到redis
     */
    fun updateRedisCache(
        context: NodeFolderJobContext,
        keyPrefix: String,
        projectId: String = StringPool.EMPTY,
        force: Boolean = false,
        collectionName: String?
    ) {
        if (!force && context.folderCache.size < 50000) return
        if (context.folderCache.isEmpty()) return
        val movedToRedis: MutableList<String> = mutableListOf()
        val storedFolderPrefix = if (collectionName.isNullOrEmpty()) {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = projectId)
        } else {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        }

        // 避免每次设置值都创建一个 Redis 连接
        redisTemplate.execute { connection ->
            val hashCommands = connection.hashCommands()
            for (entry in context.folderCache) {
                if (!entry.key.startsWith(storedFolderPrefix)) continue
                val folderInfo = extractFolderInfoFromCacheKey(entry.key, collectionName != null) ?: continue
                val sizeHKey = FolderUtils.buildCacheKey(
                    projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                    fullPath = folderInfo.fullPath, tag = SIZE
                )
                val nodeNumHKey = FolderUtils.buildCacheKey(
                    projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                    fullPath = folderInfo.fullPath, tag = NODE_NUM
                )
                val key = keyPrefix + FolderUtils.buildCacheKey(collectionName = collectionName, projectId = projectId)
                // hkey为projectId:repoName:fullPath:size或者nodenum, hvalue为对应值,
                hashCommands.hIncrBy(key.toByteArray(), sizeHKey.toByteArray(), entry.value.capSize.toLong())
                hashCommands.hIncrBy(key.toByteArray(), nodeNumHKey.toByteArray(), entry.value.nodeNum.toLong())
                movedToRedis.add(entry.key)
            }
            null
        }
        for (key in movedToRedis) {
            context.folderCache.remove(key)
        }
    }

    fun removeRedisKey(key: String) {
        redisTemplate.delete(key)
    }

    /**
     * 将memory缓存中属于collectionName下的记录写入DB中
     */
    fun storeMemoryCacheToDB(
        context: NodeFolderJobContext,
        collectionName: String,
        projectId: String = StringPool.EMPTY,
        runCollection: Boolean = false
    ) {
        if (context.folderCache.isEmpty()) {
            return
        }
        val prefix = if (runCollection) {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        } else {
            FolderUtils.buildCacheKey(projectId = projectId, repoName = StringPool.EMPTY)
        }
        val updateList = ArrayList<org.springframework.data.util.Pair<Query, Update>>()
        val storedKeys = mutableSetOf<String>()
        for (entry in context.folderCache) {
            if (!entry.key.startsWith(prefix)) continue
            extractFolderInfoFromCacheKey(entry.key, runCollection)?.let {
                storedKeys.add(entry.key)
                updateList.add(
                    buildUpdateClausesForFolder(
                        projectId = it.projectId,
                        repoName = it.repoName,
                        fullPath = it.fullPath,
                        size = entry.value.capSize.toLong(),
                        nodeNum = entry.value.nodeNum.toLong()
                    )
                )
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
        for (key in storedKeys) {
            context.folderCache.remove(key)
        }
    }

    /**
     * 将redis缓存中属于collectionName下的记录写入DB中
     */
    fun storeRedisCacheToDB(
        context: NodeFolderJobContext,
        keyPrefix: String,
        collectionName: String,
        projectId: String = StringPool.EMPTY,
        runCollection: Boolean = false
    ) {
        val keySuffix = if (runCollection) {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = projectId)
        } else {
            FolderUtils.buildCacheKey(collectionName = null, projectId = projectId)
        }
        val key = keyPrefix + keySuffix
        storeRedisCacheToDB(key, collectionName, runCollection)
    }

    /**
     * 存储对应项目下缓存在redis下的folder记录
     */
    private fun storeRedisCacheToDB(
        key: String,
        collectionName: String?,
        runCollection: Boolean = false
    ) {
        val hashOps = redisTemplate.opsForHash<String, String>()
        val updateList = ArrayList<org.springframework.data.util.Pair<Query, Update>>()
        val options = ScanOptions.scanOptions().build()
        redisTemplate.execute { connection ->
            val hashCommands = connection.hashCommands()
            val cursor = hashCommands.hScan(key.toByteArray(), options)
            while (cursor.hasNext()) {
                val entry: Map.Entry<ByteArray, ByteArray> = cursor.next()
                val folderInfo = extractFolderInfoFromCacheKey(String(entry.key), runCollection) ?: continue
                val statInfo = getFolderStatInfo(
                    key, entry, folderInfo, hashOps
                )
                updateList.add(
                    buildUpdateClausesForFolder(
                        projectId = folderInfo.projectId,
                        repoName = folderInfo.repoName,
                        fullPath = folderInfo.fullPath,
                        size = statInfo.size,
                        nodeNum = statInfo.nodeNum
                    )
                )
                if (updateList.size >= BATCH_LIMIT) {
                    mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                        .updateOne(updateList)
                        .execute()
                    updateList.clear()
                }
            }
        }
        if (updateList.isNotEmpty()) {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                .updateOne(updateList)
                .execute()
            updateList.clear()
        }
        redisTemplate.delete(key)
    }


    /**
     * 从redis中获取对应目录的统计信息
     */
    private fun getFolderStatInfo(
        key: String,
        entry: Map.Entry<ByteArray, ByteArray>,
        folderInfo: FolderInfo,
        hashOps: HashOperations<String, String, String>
    ): StatInfo {
        val size: Long
        val nodeNum: Long
        if (String(entry.key).endsWith(SIZE)) {
            val nodeNumKey = FolderUtils.buildCacheKey(
                projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                fullPath = folderInfo.fullPath, tag = NODE_NUM
            )
            size = String(entry.value).toLongOrNull() ?: 0
            nodeNum = hashOps.get(key, nodeNumKey)?.toLongOrNull() ?: 0
        } else {
            val sizeKey = FolderUtils.buildCacheKey(
                projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                fullPath = folderInfo.fullPath, tag = SIZE
            )
            nodeNum = String(entry.value).toLongOrNull() ?: 0
            size = hashOps.get(key, sizeKey)?.toLongOrNull() ?: 0
        }
        return StatInfo(size, nodeNum)
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

    data class Node(
        val id: String,
        val folder: Boolean,
        val path: String,
        val fullPath: String,
        val size: Long,
        val projectId: String,
        val repoName: String,
    )


    data class StatInfo(
        var size: Long,
        var nodeNum: Long
    )

    companion object {
        private val logger = LoggerFactory.getLogger(NodeFolderStat::class.java)
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private const val BATCH_LIMIT = 500
        private const val STORED = "stored"
        private const val REDIS_KEY_PREFIX = "node_folder_stat:"
    }
}
