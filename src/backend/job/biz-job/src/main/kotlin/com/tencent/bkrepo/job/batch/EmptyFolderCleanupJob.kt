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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.constant.CUSTOM
import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.EmptyFolderCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.EmptyFolderCleanupJobProperties
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass


/**
 * 空目录清理job
 */
@Component
@EnableConfigurationProperties(EmptyFolderCleanupJobProperties::class)
class EmptyFolderCleanupJob(
    private val properties: EmptyFolderCleanupJobProperties,
    private val activeProjectService: ActiveProjectService
): DefaultContextMongoDbJob<EmptyFolderCleanupJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.toList()
    }

    override fun buildQuery(): Query = Query()

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(row)
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        require(context is EmptyFolderCleanupJobContext)
        if (row.deleted != null) return
        if (context.activeProjects.isNotEmpty() && !properties.runAllProjects &&
            !context.activeProjects.contains(row.projectId)) return
        // 暂时只清理generic类型仓库下的空目录
        if (row.repoName !in TARGET_REPO_LIST && RepositoryCommonUtils.getRepositoryDetail(
                row.projectId, row.repoName
            ).type != RepositoryType.GENERIC) return
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

    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(14)
    }



    override fun createJobContext(): EmptyFolderCleanupJobContext {
        return EmptyFolderCleanupJobContext(
            activeProjects = if (properties.runAllProjects) {
                emptySet()
            } else {
                activeProjectService.getActiveProjects()
            },
        )
    }



    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        require(context is EmptyFolderCleanupJobContext)
        super.onRunCollectionFinished(collectionName, context)
        deleteEmptyFolders(collectionName, context)
    }


    private fun deleteEmptyFolders(
        collectionName: String,
        context: EmptyFolderCleanupJobContext
    ) {
        logger.info("will filter empty folder in table $collectionName")
        if (context.folders.isEmpty()) return
        val prefix = FolderUtils.buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        for(entry in context.folders) {
            if (!entry.key.startsWith(prefix) || entry.value.nodeNum.toLong() > 0) continue
            val folderInfo = FolderUtils.extractFolderInfoFromCacheKey(entry.key) ?: continue
            if (emptyFolderDoubleCheck(
                    projectId = folderInfo.projectId,
                    repoName = folderInfo.repoName,
                    path = folderInfo.fullPath,
                    collectionName = collectionName
                )) {
                logger.info("will delete empty folder ${folderInfo.fullPath}" +
                                " in repo ${folderInfo.projectId}|${folderInfo.repoName}")
                doEmptyFolderDelete(entry.value.id, collectionName)
                context.totalDeletedNum.increment()
            }
        }
        clearCollectionContextCache(collectionName, context)
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
        collectionName: String
    ) {
        if (objectId.isNullOrEmpty()) return
        val query = Query(
            Criteria.where(ID).isEqualTo(ObjectId(objectId))
                .and(FOLDER).isEqualTo(true)
        )
        val deleteTime = LocalDateTime.now()
        val update = Update()
            .set(LAST_MODIFIED_DATE, deleteTime)
            .set(DELETED_DATE, deleteTime)
        mongoTemplate.updateFirst(query, update, collectionName)
    }

    /**
     * 清除collection在context中对应的缓存记录
     */
    private fun clearCollectionContextCache(
        collectionName: String,
        context: EmptyFolderCleanupJobContext
    ) {
        val prefix = FolderUtils.buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        for(entry in context.folders) {
            if (entry.key.contains(prefix))
                context.folders.remove(entry.key)
        }
    }


    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val folder: Boolean,
        val fullPath: String,
        val deleted: String? = null
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Node::id.name].toString(),
            map[Node::projectId.name].toString(),
            map[Node::repoName.name].toString(),
            map[Node::folder.name] as Boolean,
            map[Node::fullPath.name].toString(),
            map[Node::deleted.name]?.toString(),
            )
    }



    companion object {
        private val logger = LoggerFactory.getLogger(EmptyFolderCleanupJob::class.java)
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        private const val COLLECTION_NAME_PREFIX = "node_"
        private val TARGET_REPO_LIST = listOf(REPORT, LOG, PIPELINE, CUSTOM, "remote-mirrors")
    }
}