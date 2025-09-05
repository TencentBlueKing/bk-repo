/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.opdata.model

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.opdata.config.OpArchiveOrGcProperties
import com.tencent.bkrepo.replication.constant.SHA256
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.stream
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Service
class GcInfoModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val opArchiveOrGcProperties: OpArchiveOrGcProperties,
) {

    private var gcInfo: Map<String, Array<Long>> = emptyMap()

    /**
     * <repo,[pre-gc,post-gc]>
     * */
    fun info(): Map<String, Array<Long>> {
        if (gcInfo.isEmpty()) {
            gcInfo = stat()
        }
        return gcInfo
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "GcInfoStatJob", lockAtMostFor = "PT24H")
    fun refresh() {
        gcInfo = stat()
    }

    private fun stat(): Map<String, Array<Long>> {
        if (!opArchiveOrGcProperties.gcEnabled) return emptyMap()
        logger.info("Start update gc metrics.")
        // 遍历节点表
        val criteria = Criteria.where("compressed").isEqualTo(true)
            .and("deleted").isEqualTo(null)
        val statistics = ConcurrentHashMap<String, Array<AtomicLong>>()
        if (opArchiveOrGcProperties.gcProjects.isEmpty()) {
            processAllProjects(statistics, criteria)
        } else {
            processSpecificProjects(statistics, criteria)
        }
        statistics[SUM] = reduce(statistics)
        logger.info("Update gc metrics successful.")
        return statistics.mapValues { arrayOf(it.value[0].get(), it.value[1].get()) }
    }

    private fun processAllProjects(statistics: ConcurrentHashMap<String, Array<AtomicLong>>, criteria: Criteria) {
        val query = Query(criteria).cursorBatchSize(BATCH_SIZE)
        forEachCollectionAsync {
            processNodes(query, it, statistics)
        }
    }

    private fun processSpecificProjects(statistics: ConcurrentHashMap<String, Array<AtomicLong>>, criteria: Criteria) {
        // 处理指定项目的gc数据
        opArchiveOrGcProperties.gcProjects.forEach { project ->
            val collectionName = "node_${HashShardingUtils.shardingSequenceFor(project, SHARDING_COUNT)}"
            val query = Query(Criteria.where("projectId").isEqualTo(project).andOperator(criteria))
                .cursorBatchSize(BATCH_SIZE)
            processNodes(query, collectionName, statistics)
        }
    }

    private fun processNodes(
        query: Query, collection: String, statistics: ConcurrentHashMap<String, Array<AtomicLong>>
    ) {
        mongoTemplate.stream<Node>(query, collection).use { nodes ->
            nodes.forEach { node ->
                updateStatistics(statistics, node)
            }
        }
    }

    private fun updateStatistics(statistics: ConcurrentHashMap<String, Array<AtomicLong>>, node: Node) {
        val repo = "${node.projectId}/${node.repoName}"
        val query2 = Query(Criteria.where(SHA256).isEqualTo(node.sha256))
        val compressInfo = mongoTemplate.findOne<CompressFile>(query2, COMPRESS_FILE_COLLECTION_NAME)
        if (compressInfo == null) {
            logger.warn("Miss ${node.sha256} compress info.")
            return
        }
        // array info: [pre-gc,post-gc]
        val longs = statistics.getOrPut(repo) { arrayOf(AtomicLong(), AtomicLong()) }
        longs[0].addAndGet(compressInfo.uncompressedSize)
        longs[1].addAndGet(compressInfo.compressedSize)
    }

    data class CompressFile(
        val sha256: String,
        val uncompressedSize: Long,
        var compressedSize: Long = -1,
    )

    data class Node(
        val projectId: String,
        val repoName: String,
        val sha256: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(GcInfoModel::class.java)
        private const val COLLECTION_NAME = "node"
        private const val COMPRESS_FILE_COLLECTION_NAME = "compress_file"
        private const val SHARDING_COUNT = 256
        private const val SUM = "SUM"
        private const val RETRY_TIMES = 3
        private const val MAX_EXECUTOR_POOL_SIZE = 64
        private const val MAX_EXECUTOR_QUEUE_SIZE = 1024
        const val BATCH_SIZE = 1000

        fun reduce(statistics: ConcurrentHashMap<String, Array<AtomicLong>>): Array<AtomicLong> {
            val longs = arrayOf(AtomicLong(), AtomicLong())
            statistics.forEachValue(1) {
                longs[0].addAndGet(it[0].get())
                longs[1].addAndGet(it[1].get())
            }
            return longs
        }

        fun forEachCollectionAsync(block: (String) -> Unit) {
            val countDownLatch = CountDownLatch(SHARDING_COUNT)
            for (i in 0 until SHARDING_COUNT) {
                pool.execute {
                    val collection = "${COLLECTION_NAME}_$i"
                    try {
                        retry(RETRY_TIMES, delayInSeconds = 10) {
                            block(collection)
                        }
                        logger.info("Process $collection done.")
                    } catch (e: Exception) {
                        logger.warn("Do on collection [$collection] error.", e)
                    } finally {
                        countDownLatch.countDown()
                    }
                }
            }
            countDownLatch.await()
        }

        private val pool: ExecutorService = ThreadPoolExecutor(
            MAX_EXECUTOR_POOL_SIZE,
            MAX_EXECUTOR_POOL_SIZE,
            1L,
            TimeUnit.MINUTES,
            ArrayBlockingQueue(MAX_EXECUTOR_QUEUE_SIZE),
            ThreadFactoryBuilder().setNameFormat("gc-info-model-%d").build(),
        )
    }
}
