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
import com.tencent.bkrepo.common.artifact.constant.CUSTOM
import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULLPATH
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.EmptyFolderChildContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils.buildCacheKey
import com.tencent.bkrepo.job.batch.utils.FolderUtils.extractFolderInfoFromCacheKey
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

/**
 * 空目录清理job
 */
class EmptyFolderCleanupJob(
    val properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {
    override fun onParentJobStart(context: ChildJobContext) {
        logger.info("start to run emptyFolderCleanupJob")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is EmptyFolderChildContext)
        if (row.deleted != null) return
        if (row.repoName !in TARGET_REPO_LIST) return
        if (row.folder) {
            val folderKey = buildCacheKey(
                collectionName = collectionName, projectId = row.projectId,
                repoName = row.repoName, fullPath = row.fullPath
            )
            val folderMetric = context.folders.getOrPut(folderKey) {
                EmptyFolderChildContext.FolderMetricsInfo(id = row.id)
            }
            folderMetric.id = row.id
        } else {
            val ancestorFolder = PathUtils.resolveAncestor(row.fullPath)
            for (folder in ancestorFolder) {
                if (folder == PathUtils.ROOT) continue
                val tempFullPath = PathUtils.toFullPath(folder)
                val folderKey = buildCacheKey(
                    collectionName = collectionName, projectId = row.projectId,
                    repoName = row.repoName, fullPath = tempFullPath
                )

                val folderMetric = context.folders.getOrPut(folderKey) { EmptyFolderChildContext.FolderMetricsInfo() }
                folderMetric.nodeNum.increment()
            }
        }
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        logger.info("emptyFolderCleanupJob finished")
    }


    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return EmptyFolderChildContext(parentJobContext)
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        require(context is EmptyFolderChildContext)
        super.onRunCollectionFinished(collectionName, context)
        filterEmptyFolders(collectionName, context)
    }


    private fun filterEmptyFolders(
        collectionName: String,
        context: EmptyFolderChildContext
    ) {
        logger.info("will filter empty folder in table $collectionName")
        if (context.folders.isEmpty()) return
        val prefix = buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        for(entry in context.folders) {
            if (!entry.key.startsWith(prefix) || entry.value.nodeNum.toLong() > 0) continue
            extractFolderInfoFromCacheKey(entry.key)?.let {
                if (emptyFolderDoubleCheck(
                        projectId = it.projectId,
                        repoName = it.repoName,
                        path = it.fullPath,
                        collectionName = collectionName
                )) {
                    logger.info("will delete empty folder ${it.fullPath} in repo ${it.projectId}|${it.repoName}")
                    deleteEmptyFolders(entry.value.id, collectionName)
                }
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
            .and(FULLPATH).regex("^${PathUtils.escapeRegex(nodePath)}")
            .and(FOLDER).isEqualTo(false)

        val query = Query(criteria).withHint(FULL_PATH_IDX)
        val result = mongoTemplate.find(query, Map::class.java, collectionName)
        return result.isNullOrEmpty()
    }


    /**
     * 删除空目录
     */
    private fun deleteEmptyFolders(
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
        context: EmptyFolderChildContext
    ) {
        val prefix = buildCacheKey(collectionName = collectionName, projectId = StringPool.EMPTY)
        for(entry in context.folders) {
            if (entry.key.contains(prefix))
                context.folders.remove(entry.key)
        }
    }



    companion object {
        private val logger = LoggerHolder.jobLogger
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        private val TARGET_REPO_LIST = listOf(REPORT, LOG, PIPELINE, CUSTOM)

    }
}
