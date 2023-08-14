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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.CREATED_DATE
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PATH
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.FolderSizeChildContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils.shardingSequence
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.DayOfWeek
import java.time.LocalDateTime

class FolderSizeStatChildJob(
    properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
) : ChildMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {


    private val cache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .removalListener<Triple<String, String, String>, Pair<Long, Long>> {
            if (it.cause == RemovalCause.REPLACED) return@removalListener
            logger.info("remove ${it.key}, ${it.value}, cause ${it.cause}")
            updateFolderSize(
                projectId = it.key!!.first,
                repoName = it.key!!.second,
                fullPath = it.key!!.third,
                size = it.value.first,
                nodeNum = it.value.second
            )
        }
        .build<Triple<String, String, String>, Pair<Long, Long>>()

    override fun onParentJobStart(context: ChildJobContext) {
        require(context is FolderSizeChildContext)
        initCheck(context)
        logger.info("start to stat the size of folder, init flag is ${context.initFlag}")
    }

    override fun run(row: NodeStatCompositeMongoDbBatchJob.Node, collectionName: String, context: JobContext) {
        require(context is FolderSizeChildContext)
        if (context.initFlag) return
        if (row.deleted != null || row.folder || row.repoName in listOf(REPORT, LOG)) {
            return
        }

        initAncestor(row.projectId, row.repoName, row.path)
        val folderPath = if (row.path == PathUtils.ROOT) {
            PathUtils.ROOT
        } else {
            row.path.removeSuffix(StringPool.SLASH)
        }
        val key = Triple(row.projectId, row.repoName, folderPath)
        val (folderSize, nodeNum) = cache.getIfPresent(key) ?: Pair(0L, 0L)
        cache.put(key, Pair(folderSize+row.size, nodeNum+1))
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is FolderSizeChildContext)
        cache.invalidateAll()
        logger.info("stat size of folder done")
    }


    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return FolderSizeChildContext(parentJobContext)
    }

    private fun initCheck(context: FolderSizeChildContext) {
        if (LocalDateTime.now().dayOfWeek != DayOfWeek.MONDAY) {
            return
        }
        context.initFlag = false
        for (i in 0 until SHARDING_COUNT) {
            mongoTemplate.remove(Query(), "$COLLECTION_FOLDER_SIZE_STAT_PREFIX$i")
        }
    }

    /**
     * 初始化当前目录的上级目录数据
     */
    private fun initAncestor(
        projectId: String,
        repoName: String,
        path: String
    ) {
        if (path == PathUtils.ROOT) return
        val folderList = PathUtils.resolveAncestor(path).map {
            if (it != PathUtils.ROOT) {
                it.removeSuffix(StringPool.SLASH)
            } else {
                it
            }
        }
        folderList.forEach {
            val key = Triple(projectId, repoName, it)
            cache.getIfPresent(key) ?: run {
                cache.put(key, Pair(0, 0))
            }
        }
    }

    private fun updateFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ) {
        val query = Query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
                .and(FOLDER_PATH).isEqualTo(fullPath)
        )
        var path = PathUtils.normalizeFullPath(PathUtils.resolveParent(fullPath))
        if (fullPath == PathUtils.ROOT) path = StringPool.EMPTY
        val update = Update().inc(FOLDER_SIZE, size)
            .inc(NODE_NUM, nodeNum)
            .setOnInsert(CREATED_DATE, LocalDateTime.now())
            .setOnInsert(PATH, path)
            .set(LAST_MODIFIED_DATE, LocalDateTime.now())
        val folderCollectionName = COLLECTION_FOLDER_SIZE_STAT_PREFIX + shardingSequence(projectId, SHARDING_COUNT)
        mongoTemplate.upsert(query, update, folderCollectionName)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_FOLDER_SIZE_STAT_PREFIX = "folder_stat_"
        private const val FOLDER_PATH = "folderPath"
        private const val FOLDER_SIZE = "size"
        private const val NODE_NUM = "nodeNum"
    }
}
