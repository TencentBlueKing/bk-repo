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

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils.combinePath
import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveAncestor
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.job.pojo.EmptyFolderMetric
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service
class EmptyFolderStatJob(
    private val mongoTemplate: MongoTemplate
) : BaseJob(mongoTemplate) {

    private var executor: ThreadPoolExecutor? = null

    fun statFolderSize(projectId: String, repoName: String, path: String = StringPool.SLASH): List<EmptyFolderMetric> {
        logger.info("start to stat empty folder record under $path in repo $projectId|$repoName")
        val index = HashShardingUtils.shardingSequenceFor(projectId, 256)
        return stat(
            shardingIndex = index,
            projectId = projectId,
            repoName = repoName,
            path = path
        )
    }

    fun deleteEmptyFolder(projectId: String, repoName: String, objectId: String) {
        val index = HashShardingUtils.shardingSequenceFor(projectId, 256)
        val collectionName = collectionName(index)
        val query = Query(
            Criteria.where(FIELD_NAME_ID).isEqualTo(ObjectId(objectId))
        )
        mongoTemplate.remove(query, Map::class.java, collectionName)
    }

    private fun stat(
        shardingIndex: Int,
        projectId: String,
        repoName: String,
        path: String
    ): List<EmptyFolderMetric> {
        val lastId = AtomicReference<String>()
        val startIds = LinkedBlockingQueue<String>(DEFAULT_ID_QUEUE_SIZE)
        val collectionName = collectionName(shardingIndex)

        val folderMetricsList = if (submitId(lastId, startIds, collectionName, DEFAULT_BATCH_SIZE)) {
            doStat(lastId, startIds, collectionName, projectId, repoName, path)
        } else {
            emptyList()
        }.ifEmpty { return emptyList() }
        return folderMetricsList
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun doStat(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        nodeCollectionName: String,
        projectId: String,
        repoName: String,
        parentPath: String
    ): List<EmptyFolderMetric> {
        refreshExecutor()

        // 统计数据
        val folderMetrics = ConcurrentHashMap<String, EmptyFolderMetric>()
        val futures = ArrayList<Future<*>>()

        while (true) {
            val startId = startIds.poll(1, TimeUnit.SECONDS)
                ?: // lastId为null表示id遍历提交未结束，等待新id入队
                if (lastId.get() == null) {
                    continue
                } else {
                    break
                }

            val future = executor!!.submit {
                val query = Query(
                    Criteria.where(FIELD_NAME_ID).gte(ObjectId(startId))
                        .and(NodeDetail::projectId.name).`is`(projectId)
                        .and(NodeDetail::repoName.name).`is`(repoName)
                ).with(Sort.by(FIELD_NAME_ID))
                    .limit(DEFAULT_BATCH_SIZE)
                query.fields().include(
                    NodeDetail::size.name, NodeDetail::folder.name, NodeDetail::path.name,
                    NodeDetail::name.name, NodeDetail::fullPath.name, FIELD_NAME_DELETED,
                    FIELD_NAME_ID
                )
                val nodes = mongoTemplate.find(query, Map::class.java, nodeCollectionName)
                nodes.forEach {
                    val deleted = it[FIELD_NAME_DELETED]
                    if (deleted == null) {
                        val name = it[NodeDetail::name.name].toString()
                        val path = it[NodeDetail::path.name].toString()
                        val isFolder = it[NodeDetail::folder.name].toString().toBoolean()
                        val fullPath = it[NodeDetail::fullPath.name].toString()
                        val objectId = it[FIELD_NAME_ID].toString()
                        if (fullPath.startsWith(parentPath)) {
                            if (isFolder) {
                                folderMetrics.getOrPut(combinePath(path, name)) {
                                    EmptyFolderMetric(fullPath, objectId)
                                }
                            } else {
                                resolveAncestor(fullPath).forEach { str ->
                                    folderMetrics.remove(str)
                                }
                            }
                        }
                    }
                }
            }
            futures.add(future)
        }

        // 等待所有任务结束
        futures.forEach { it.get() }
        return folderMetrics.values.toList()
    }

    private fun collectionName(shardingIndex: Int): String = "${TABLE_PREFIX}$shardingIndex"

    @Synchronized
    private fun refreshExecutor() {
        if (executor == null) {
            executor = ThreadPoolExecutor(
                DEFAULT_THREAD_POOL_SIZE,
                DEFAULT_THREAD_POOL_SIZE,
                DEFAULT_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                LinkedBlockingQueue(DEFAULT_THREAD_POOL_QUEUE_CAPACITY),
                ThreadPoolExecutor.CallerRunsPolicy()
            )
            executor!!.allowCoreThreadTimeOut(true)
        } else if (executor!!.maximumPoolSize != DEFAULT_THREAD_POOL_SIZE) {
            executor!!.corePoolSize = DEFAULT_THREAD_POOL_SIZE
            executor!!.maximumPoolSize = DEFAULT_THREAD_POOL_SIZE
        }
    }

    private

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val TABLE_PREFIX = "node_"
        private const val FIELD_NAME_DELETED = "deleted"
        private const val DEFAULT_ID_QUEUE_SIZE = 10000
        private const val DEFAULT_THREAD_POOL_SIZE = 1
        private const val DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 60L
        private const val DEFAULT_THREAD_POOL_QUEUE_CAPACITY = 1000
        private const val DEFAULT_BATCH_SIZE = 50000
    }
}
