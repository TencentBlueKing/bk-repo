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

import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULLPATH
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.FolderChildContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils.shardingSequence
import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock

class FolderStatChildJob(
    properties: CompositeJobProperties,
    private val mongoTemplate: MongoTemplate,
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
        if (row.deleted != null || row.folder || row.repoName in listOf(REPORT, LOG)) {
            return
        }

        // 将缓存的信息更新到节点中
        storeCache(context = context)

        // 更新当前节点所有上级目录统计信息
        PathUtils.resolveAncestorFolder(row.fullPath).forEach {
            if (it != PathUtils.ROOT) {
                val key = FolderChildContext.FolderInfo(row.projectId, row.repoName, it)
                val folderMetrics = context.folderCache.getOrPut(key) { FolderChildContext.FolderMetrics() }
                folderMetrics.capSize.add(row.size)
                folderMetrics.nodeNum.increment()
            }
        }
    }

    override fun onParentJobFinished(context: ChildJobContext) {
        require(context is FolderChildContext)
        storeCache(context = context, force = true)
        logger.info("stat size of folder done")
    }


    override fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return FolderChildContext(parentJobContext)
    }


    private fun storeCache(force: Boolean = false, context: FolderChildContext) {
        if (context.folderCache.isEmpty() || !force && context.folderCache.size < CACHE_SIZE) {
            return
        }
        try {
            lock.lock()
            context.folderCache.entries.forEach {
                updateFolderSize(
                    projectId = it.key.projectId,
                    repoName = it.key.repoName,
                    fullPath = it.key.fullPath,
                    size = it.value.capSize.toLong(),
                    nodeNum = it.value.nodeNum.toLong()
                )
            }
            context.folderCache.clear()
        } finally {
            lock.unlock()
        }

    }

    private fun initCheck(context: FolderChildContext) {
//        if (LocalDateTime.now().dayOfWeek != DayOfWeek.MONDAY) {
//            return
//        }
        context.initFlag = false
        // TODO 如何重复初始化
        for (i in 0 until SHARDING_COUNT) {
            restoreFolderStat("${COLLECTION_NODE_PREFIX}$i")
        }
    }


    private fun restoreFolderStat(nodeCollectionName: String) {
        val query = Query(
            Criteria.where(FOLDER).isEqualTo(true)
                .and(DELETED_DATE).isEqualTo(null)
        )
        val update = Update().set(SIZE, 0)
            .unset(NODE_NUM)
        mongoTemplate.updateMulti(query, update, nodeCollectionName)
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
                .and(FULLPATH).isEqualTo(fullPath)
                .and(DELETED_DATE).isEqualTo(null)
                .and(FOLDER).isEqualTo(true)
        )
        val update = Update().inc(SIZE, size)
            .inc(NODE_NUM, nodeNum)
            .set(LAST_MODIFIED_DATE, LocalDateTime.now())
        val nodeCollectionName = COLLECTION_NODE_PREFIX + shardingSequence(projectId, SHARDING_COUNT)
        mongoTemplate.updateFirst(query, update, nodeCollectionName)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val SIZE = "size"
        private const val NODE_NUM = "nodeNum"
        private const val CACHE_SIZE = 2000L
    }
}
