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
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SIZE
import com.tencent.bkrepo.job.batch.context.EmptyFolderCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.FolderUtils.extractFolderInfoFromCacheKey
import com.tencent.bkrepo.job.pojo.FolderInfo
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class EmptyFolderCleanup(
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

    fun removeRedisKey(key: String) {
        redisTemplate.delete(key)
    }

    fun collectEmptyFolderWithMemory(
        row: Node,
        context: EmptyFolderCleanupJobContext,
        useMemory: Boolean,
        keyPrefix: String,
        collectionName: String? = null,
        cacheNumLimit: Long,
        ) {
        if (row.folder) {
            val folderKey = FolderUtils.buildCacheKey(
                collectionName = collectionName, projectId = row.projectId,
                repoName = row.repoName, fullPath = row.fullPath
            )
            val folderMetric = context.folders.getOrPut(folderKey) {
                EmptyFolderCleanupJobContext.FolderMetricsInfo(id = row.id)
            }
            folderMetric.id = row.id
        } else {
            val ancestorFolder = PathUtils.resolveAncestor(row.fullPath)
            for (folder in ancestorFolder) {
                if (folder == PathUtils.ROOT) continue
                val tempFullPath = PathUtils.toFullPath(folder)
                val folderKey = FolderUtils.buildCacheKey(
                    collectionName = collectionName, projectId = row.projectId,
                    repoName = row.repoName, fullPath = tempFullPath
                )
                val folderMetric = context.folders.getOrPut(folderKey) {
                    EmptyFolderCleanupJobContext.FolderMetricsInfo()
                }
                folderMetric.nodeNum.increment()
            }
        }
        if (!useMemory) {
            collectEmptyFolderWithRedis(
                context = context,
                keyPrefix = keyPrefix,
                projectId = row.projectId,
                collectionName = collectionName,
                cacheNumLimit = cacheNumLimit
            )
        }
    }

    /**
     * 将存储在内存中的临时记录更新到redis
     */
    fun collectEmptyFolderWithRedis(
        context: EmptyFolderCleanupJobContext,
        force: Boolean = false,
        keyPrefix: String,
        projectId: String = StringPool.EMPTY,
        collectionName: String? = null,
        cacheNumLimit: Long,
        ) {
        if (!force && context.folders.size < cacheNumLimit) return
        if (context.folders.isEmpty()) return
        val movedToRedis: MutableList<String> = mutableListOf()
        val storedFolderPrefix = if (collectionName.isNullOrEmpty()) {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = projectId) + StringPool.COLON
        } else {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        }
        // 避免每次设置值都创建一个 Redis 连接
        redisTemplate.execute { connection ->
            val hashCommands = connection.hashCommands()
            for (entry in context.folders) {
                if (!entry.key.startsWith(storedFolderPrefix)) continue
                val folderInfo = extractFolderInfoFromCacheKey(entry.key, collectionName != null) ?: continue
                val nodeNumHKey = FolderUtils.buildCacheKey(
                    projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                    fullPath = folderInfo.fullPath, tag = NODE_NUM
                )

                val key = keyPrefix + StringPool.COLON + FolderUtils.buildCacheKey(
                    collectionName = collectionName, projectId = projectId
                )
                hashCommands.hIncrBy(key.toByteArray(), nodeNumHKey.toByteArray(), entry.value.nodeNum.toLong())
                entry.value.id?.let {
                    val idHKey = FolderUtils.buildCacheKey(
                        projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                        fullPath = folderInfo.fullPath, tag = NODE_ID
                    )
                    hashCommands.hSet(key.toByteArray(), idHKey.toByteArray(), entry.value.id!!.toByteArray())
                }
                movedToRedis.add(entry.key)
            }
            null
        }
        for (key in movedToRedis) {
            context.folders.remove(key)
        }
        movedToRedis.clear()
    }

    fun emptyFolderHandlerWithMemory(
        collection: String,
        context: EmptyFolderCleanupJobContext,
        deletedEmptyFolder: Boolean,
        deleteFolderRepos: List<String>,
        projectId: String = StringPool.EMPTY,
        runCollection: Boolean = false,
    ) {
        if (context.folders.isEmpty()) return
        val prefix = if (runCollection) {
            FolderUtils.buildCacheKey(collectionName = collection, projectId = StringPool.EMPTY)
        } else {
            FolderUtils.buildCacheKey(projectId = projectId, repoName = StringPool.EMPTY)
        }
        for (entry in context.folders) {
            if (!entry.key.startsWith(prefix) || entry.value.nodeNum.toLong() > 0) continue
            val folderInfo = extractFolderInfoFromCacheKey(entry.key, runCollection) ?: continue
            emptyFolderHandler(
                folderInfo = folderInfo,
                context = context,
                collection = collection,
                deleteFolderRepos = deleteFolderRepos,
                deletedEmptyFolder = deletedEmptyFolder,
                id = entry.value.id!!,
            )
        }
        clearContextCache(projectId, context, collection, runCollection)
    }

    fun emptyFolderHandlerWithRedis(
        collection: String,
        keyPrefix: String,
        deletedEmptyFolder: Boolean,
        deleteFolderRepos: List<String>,
        context: EmptyFolderCleanupJobContext,
        runCollection: Boolean = false,
        projectId: String = StringPool.EMPTY,
    ) {

        val keySuffix = if (runCollection) {
            FolderUtils.buildCacheKey(collectionName = collection, projectId = projectId)
        } else {
            FolderUtils.buildCacheKey(collectionName = null, projectId = projectId)
        }
        val key = keyPrefix + StringPool.COLON + keySuffix
        val hashOps = redisTemplate.opsForHash<String, String>()
        val options = ScanOptions.scanOptions().build()
        redisTemplate.execute { connection ->
            val hashCommands = connection.hashCommands()
            val cursor = hashCommands.hScan(key.toByteArray(), options)
            while (cursor.hasNext()) {
                val entry: Map.Entry<ByteArray, ByteArray> = cursor.next()
                val keyStr = String(entry.key).substringBeforeLast(StringPool.COLON)
                val folderInfo = extractFolderInfoFromCacheKey(keyStr) ?: continue
                val statInfo = getFolderStatInfo(
                    key, entry, folderInfo, hashOps
                )
                if (statInfo.nodeNum > 0) continue
                emptyFolderHandler(
                    folderInfo = folderInfo,
                    context = context,
                    collection = collection,
                    deleteFolderRepos = deleteFolderRepos,
                    deletedEmptyFolder = deletedEmptyFolder,
                    id = statInfo.id!!,
                )
            }
        }
        redisTemplate.delete(key)
    }

    private fun emptyFolderHandler(
        folderInfo: FolderInfo,
        collection: String,
        deletedEmptyFolder: Boolean,
        deleteFolderRepos: List<String>,
        id: String,
        context: EmptyFolderCleanupJobContext,
    ) {
        if (emptyFolderDoubleCheck(
                projectId = folderInfo.projectId,
                repoName = folderInfo.repoName,
                path = folderInfo.fullPath,
                collectionName = collection
            )) {
            val deletedFlag = deletedFolderFlag(
                repoName = folderInfo.repoName,
                deletedEmptyFolder = deletedEmptyFolder,
                deleteFolderRepos = deleteFolderRepos
            )
            logger.info(
                "will delete empty folder ${folderInfo.fullPath}" +
                    " in repo ${folderInfo.projectId}|${folderInfo.repoName} " +
                    "with config deletedFlag: $deletedFlag"
            )
            doEmptyFolderDelete(id, collection, deletedFlag)
            context.totalDeletedNum.increment()
        }
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
        val id: String
        val nodeNum: Long
        if (String(entry.key).endsWith(SIZE)) {
            val nodeNumKey = FolderUtils.buildCacheKey(
                projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                fullPath = folderInfo.fullPath, tag = NODE_NUM
            )
            id = String(entry.value)
            nodeNum = hashOps.get(key, nodeNumKey)?.toLongOrNull() ?: 0
        } else {
            val idKey = FolderUtils.buildCacheKey(
                projectId = folderInfo.projectId, repoName = folderInfo.repoName,
                fullPath = folderInfo.fullPath, tag = NODE_ID
            )
            nodeNum = String(entry.value).toLongOrNull() ?: 0
            id = hashOps.get(key, idKey) ?: StringPool.EMPTY
        }
        return StatInfo(id, nodeNum)
    }

    /**
     * 只针对指定的generic仓库可以进行删除
     * 即使全局配置的deletedEmptyFolder是true
     */
    private fun deletedFolderFlag(
        repoName: String, deletedEmptyFolder: Boolean,
        deleteFolderRepos: List<String>,
    ): Boolean {
        // 暂时只删除特殊仓库下的空目录
        return (repoName in deleteFolderRepos) && deletedEmptyFolder
    }

    /**
     * 再次确认过滤出的空目录下是否包含文件
     */
    private fun emptyFolderDoubleCheck(
        projectId: String,
        repoName: String,
        path: String,
        collectionName: String
    ): Boolean {
        val nodePath = PathUtils.toPath(path)
        val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
            .and(DELETED_DATE).isEqualTo(null)
            .and(FULL_PATH).regex("^${PathUtils.escapeRegex(nodePath)}")
            .and(FOLDER).isEqualTo(false)

        val query = Query(criteria).withHint(FULL_PATH_IDX)
        val result = mongoTemplate.find(query, Map::class.java, collectionName)
        return result.isNullOrEmpty()
    }

    /**
     * 删除空目录
     */
    private fun doEmptyFolderDelete(
        objectId: String?,
        collectionName: String,
        deletedEmptyFolder: Boolean,
    ) {
        if (objectId.isNullOrEmpty()) return
        val query = Query(
            Criteria.where(ID).isEqualTo(ObjectId(objectId))
                .and(FOLDER).isEqualTo(true)
        )
        val deleteTime = LocalDateTime.now()
        val update = Update().set(LAST_MODIFIED_DATE, deleteTime)
        if (deletedEmptyFolder) {
            update.set(DELETED_DATE, deleteTime)
        } else {
            update.set(SIZE, 0).set(NODE_NUM, 0)
        }
        try {
            mongoTemplate.updateFirst(query, update, collectionName)
        } catch (e: Exception) {
            logger.error("delete $objectId in collection $collectionName failed, error: $e")
        }
    }

    /**
     * 清除collection在context中对应的缓存记录
     */
    private fun clearContextCache(
        projectId: String,
        context: EmptyFolderCleanupJobContext,
        collectionName: String,
        runCollection: Boolean = false
    ) {
        val prefix = if (runCollection) {
            FolderUtils.buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        } else {
            FolderUtils.buildCacheKey(projectId = projectId, repoName = StringPool.EMPTY)
        }
        for (entry in context.folders) {
            if (entry.key.startsWith(prefix))
                context.folders.remove(entry.key)
        }
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
        var id: String?,
        var nodeNum: Long
    )


    companion object {
        private val logger = LoggerFactory.getLogger(EmptyFolderCleanup::class.java)
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        private const val NODE_NUM = "nodeNum"
        private const val NODE_ID = "nodeId"

    }
}
