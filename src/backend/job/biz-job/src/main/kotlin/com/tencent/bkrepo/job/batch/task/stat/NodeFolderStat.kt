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
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class NodeFolderStat(
    private val mongoTemplate: MongoTemplate,
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
        collectionName: String? = null
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

    companion object {
        private val logger = LoggerFactory.getLogger(NodeFolderStat::class.java)
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private const val BATCH_LIMIT = 500
    }
}
