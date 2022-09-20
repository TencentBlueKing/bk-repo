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

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.opdata.config.OpStatJobProperties
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

open class BaseJob<T>(
    private val mongoTemplate: MongoTemplate,
    var opJobProperties: OpStatJobProperties
) {
    private val submitIdExecutor = ThreadPoolExecutor(
        0, 1, 0L, TimeUnit.MILLISECONDS, SynchronousQueue()
    )

    var executor: ThreadPoolExecutor? = null

    fun submitId(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        collectionName: String,
        batchSize: Int
    ): Boolean {
        val initId = nextId(null, 0, collectionName) ?: return false
        submitIdExecutor.execute {
            var startId: String? = initId
            var preId: String? = null
            var submittedIdCount = 0L
            while (startId != null) {
                submittedIdCount++
                startIds.add(startId)
                preId = startId
                // 获取下一个id
                startId = nextId(startId, batchSize, collectionName)
            }
            lastId.set(preId)
        }
        return true
    }

    fun nextId(
        startId: String? = null,
        skip: Int,
        collectionName: String
    ): String? {
        val criteria = Criteria()
        startId?.let { criteria.and(FIELD_NAME_ID).gte(ObjectId(it)) }
        val query = Query(criteria).with(Sort.by(FIELD_NAME_ID)).skip(skip.toLong())
        query.fields().include(FIELD_NAME_ID)
        return mongoTemplate.findOne(query, Map::class.java, collectionName)?.get(FIELD_NAME_ID)?.toString()
    }

    @Synchronized
    fun refreshExecutor() {
        if (executor == null) {
            executor = ThreadPoolExecutor(
                opJobProperties.threadPoolSize,
                opJobProperties.threadPoolSize,
                DEFAULT_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                LinkedBlockingQueue(DEFAULT_THREAD_POOL_QUEUE_CAPACITY),
                ThreadPoolExecutor.CallerRunsPolicy()
            )
            executor!!.allowCoreThreadTimeOut(true)
        } else if (executor!!.maximumPoolSize != opJobProperties.threadPoolSize) {
            executor!!.corePoolSize = opJobProperties.threadPoolSize
            executor!!.maximumPoolSize = opJobProperties.threadPoolSize
        }
    }

    fun stat(
        shardingIndex: Int? = null,
        runConcurrency: Boolean = true,
        extraInfo: Map<String, String> = emptyMap()
    ): List<T> {
        val lastId = AtomicReference<String>()
        val startIds = LinkedBlockingQueue<String>(DEFAULT_ID_QUEUE_SIZE)
        val collectionName = collectionName(shardingIndex)

        return if (submitId(lastId, startIds, collectionName, opJobProperties.batchSize)) {
            if (runConcurrency) {
                doStatWithExecutor(
                    lastId = lastId,
                    startIds = startIds,
                    collectionName = collectionName,
                    extraInfo = extraInfo
                ).values.toList()
            } else {
                doStat(
                    lastId = lastId,
                    startIds = startIds,
                    collectionName = collectionName
                ).values.toList()
            }
        } else {
            emptyList()
        }.ifEmpty { return emptyList() }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    fun doStatWithExecutor(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        collectionName: String,
        extraInfo: Map<String, String> = emptyMap()
    ): Map<String, T> {
        refreshExecutor()
        // 统计数据
        val metrics = ConcurrentHashMap<String, T>()
        val folderSets = ConcurrentHashSet<String>()
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
                statAction(
                    startId = startId,
                    collectionName = collectionName,
                    metrics = metrics,
                    extraInfo = extraInfo,
                    folderSets = folderSets
                )
            }
            futures.add(future)
        }

        // 等待所有任务结束
        futures.forEach { it.get() }
        return mergeResult(metrics, folderSets)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun doStat(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        collectionName: String
    ): Map<String, T> {
        // 统计数据
        val metrics = ConcurrentHashMap<String, T>()
        while (true) {
            val startId = startIds.poll(1, TimeUnit.SECONDS)
                ?: // lastId为null表示id遍历提交未结束，等待新id入队
                if (lastId.get() == null) {
                    continue
                } else {
                    break
                }
            statAction(
                startId = startId,
                collectionName = collectionName,
                metrics = metrics
            )
        }
        return metrics
    }

    open fun statAction(
        startId: String,
        collectionName: String,
        metrics: ConcurrentHashMap<String, T>,
        extraInfo: Map<String, String> = emptyMap(),
        folderSets: ConcurrentHashSet<String> = ConcurrentHashSet()
    ) {
    }

    open fun mergeResult(
        metrics: ConcurrentHashMap<String, T>,
        folderSets: ConcurrentHashSet<String>
    ): ConcurrentHashMap<String, T> {
        if (folderSets.isEmpty()) return metrics
        metrics.keys.forEach {
            if (folderSets.contains(it)) {
                metrics.remove(it)
            }
        }
        return metrics
    }

    open fun collectionName(shardingIndex: Int? = null): String {
        return StringPool.EMPTY
    }
    companion object {
        const val FIELD_NAME_ID = AbstractMongoDao.ID
        private const val DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 60L
        private const val DEFAULT_THREAD_POOL_QUEUE_CAPACITY = 1000
        private const val DEFAULT_ID_QUEUE_SIZE = 10000
        const val FIELD_NAME_DELETED = "deleted"
    }
}
