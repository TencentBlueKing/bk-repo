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
import com.tencent.bkrepo.job.NODE_NUM
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SIZE
import com.tencent.bkrepo.job.batch.context.EmptyFolderCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.FolderUtils.extractFolderInfoFromCacheKey
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class EmptyFolderCleanup(
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

    fun collectEmptyFolder(
        row: Node,
        context: EmptyFolderCleanupJobContext,
        collectionName: String? = null
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
    }

    fun emptyFolderHandler(
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
                doEmptyFolderDelete(entry.value.id, collection, deletedFlag)
                context.totalDeletedNum.increment()
            }
        }
        clearContextCache(projectId, context, collection, runCollection)
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

    companion object {
        private val logger = LoggerFactory.getLogger(EmptyFolderCleanup::class.java)
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
    }
}
