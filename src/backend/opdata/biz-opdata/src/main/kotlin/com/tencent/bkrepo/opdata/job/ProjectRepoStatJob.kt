/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.InfluxDbConfig
import com.tencent.bkrepo.opdata.config.OpProjectRepoStatJobProperties
import com.tencent.bkrepo.opdata.job.pojo.ProjectMetrics
import com.tencent.bkrepo.opdata.model.RepoModel
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.bson.types.ObjectId
import org.influxdb.dto.BatchPoints
import org.influxdb.dto.Point
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * stat bkrepo running status
 */
@Component
class ProjectRepoStatJob(
    private val repoModel: RepoModel,
    private val influxDbConfig: InfluxDbConfig,
    private val projectMetricsRepository: ProjectMetricsRepository,
    private val mongoTemplate: MongoTemplate,
    private val opJobProperties: OpProjectRepoStatJobProperties
) {

    private val submitIdExecutor = ThreadPoolExecutor(
        0, 1, 0L, TimeUnit.MILLISECONDS, SynchronousQueue()
    )

    private var executor: ThreadPoolExecutor? = null

    @Scheduled(cron = "00 00 */1 * * ?")
    @SchedulerLock(name = "ProjectRepoStatJob", lockAtMostFor = "PT10H")
    fun statProjectRepoSize() {
        if (!opJobProperties.enabled) {
            logger.info("stat project repo size job was disabled")
            return
        }
        logger.info("start to stat project metrics")
        val influxDb = influxDbConfig.influxDbUtils().getInstance() ?: run {
            logger.error("init influxdb fail")
            return
        }
        val timeMillis = System.currentTimeMillis()
        val batchPoints = BatchPoints
            .database(influxDbConfig.database)
            .build()
        val projectMetricsList = mutableListOf<TProjectMetrics>()

        for (i in 0 until SHARDING_COUNT) {
            projectMetricsList.addAll(stat(i, batchPoints, timeMillis))
        }

        // 数据写入 influxdb
        logger.info("start to insert influxdb metrics ")
        influxDb.write(batchPoints)
        influxDb.close()

        // 数据写入mongodb统计表
        projectMetricsRepository.deleteAll()
        logger.info("start to insert  mongodb metrics ")
        projectMetricsRepository.insert(projectMetricsList)
        logger.info("stat project metrics done")
    }

    private fun stat(shardingIndex: Int, batchPoints: BatchPoints, timeMillis: Long): List<TProjectMetrics> {
        val lastId = AtomicReference<String>()
        val startIds = LinkedBlockingQueue<String>(DEFAULT_ID_QUEUE_SIZE)
        val collectionName = collectionName(shardingIndex)

        val projectMetricsList = if (submitId(lastId, startIds, collectionName)) {
            doStat(lastId, startIds, collectionName)
        } else {
            emptyList()
        }.ifEmpty { return emptyList() }

        val tProjectMetricsList = ArrayList<TProjectMetrics>(projectMetricsList.size)
        for (projectMetrics in projectMetricsList) {
            val projectNodeNum = projectMetrics.nodeNum.toLong()
            val projectCapSize = projectMetrics.capSize.toLong()
            if (projectNodeNum == 0L || projectCapSize == 0L) {
                // 只统计有效项目数据
                continue
            }
            val repoMetrics = ArrayList<RepoMetrics>(projectMetrics.repoMetrics.size)
            projectMetrics.repoMetrics.values.forEach { repo ->
                val num = repo.num.toLong()
                val size = repo.size.toLong()
                // 有效仓库的统计数据
                if (num != 0L && size != 0L) {
                    logger.info("project : [${projectMetrics.projectId}],repo: [${repo.repoName}],size:[$repo]")
                    val point = Point.measurement(INFLUX_COLLECION)
                        .time(timeMillis, TimeUnit.MILLISECONDS)
                        .addField("size", size / TOGIGABYTE)
                        .addField("num", num)
                        .tag("projectId", projectMetrics.projectId)
                        .tag("repoName", repo.repoName)
                        .tag("table", collectionName)
                        .build()
                    batchPoints.point(point)
                    repoMetrics.add(RepoMetrics(repo.repoName, repo.credentialsKey, size / TOGIGABYTE, num))
                }
            }
            tProjectMetricsList.add(
                TProjectMetrics(
                    projectMetrics.projectId, projectNodeNum, projectCapSize / TOGIGABYTE, repoMetrics
                )
            )
        }
        return tProjectMetricsList
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun doStat(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        nodeCollectionName: String
    ): List<ProjectMetrics> {
        refreshExecutor()
        // 用于日志输出
        val totalCount = AtomicLong()
        val livedNodeCount = AtomicLong()
        val start = System.currentTimeMillis()

        // 统计数据
        val projectMetrics = ConcurrentHashMap<String, ProjectMetrics>()
        val futures = ArrayList<Future<*>>()

        while (true) {
            val startId = startIds.poll(1, TimeUnit.SECONDS)
            if (startId == null) {
                // lastId为null表示id遍历提交未结束，等待新id入队
                if (lastId.get() == null) {
                    continue
                } else {
                    break
                }
            }

            val future = executor!!.submit {
                val query = Query(Criteria.where(FIELD_NAME_ID).gte(ObjectId(startId)))
                    .with(Sort.by(FIELD_NAME_ID))
                    .limit(opJobProperties.batchSize)
                query.fields().include(
                    NodeDetail::projectId.name, NodeDetail::repoName.name, NodeDetail::size.name, FIELD_NAME_DELETED
                )
                val nodes = mongoTemplate.find(query, Map::class.java, nodeCollectionName)
                nodes.forEach {
                    val deleted = it[FIELD_NAME_DELETED]
                    totalCount.incrementAndGet()
                    if (deleted == null) {
                        val projectId = it[NodeDetail::projectId.name].toString()
                        val repoName = it[NodeDetail::repoName.name].toString()
                        val size = it[NodeDetail::size.name].toString().toLong()
                        val credentialsKey = repoModel.getRepoInfo(projectId, repoName)?.credentialsKey ?: "default"
                        livedNodeCount.incrementAndGet()
                        projectMetrics
                            .getOrPut(projectId) { ProjectMetrics(projectId) }
                            .apply {
                                capSize.add(size)
                                nodeNum.increment()
                                val repo = repoMetrics.getOrPut(repoName) {
                                    com.tencent.bkrepo.opdata.job.pojo.RepoMetrics(repoName, credentialsKey)
                                }
                                repo.size.add(size)
                                repo.num.increment()
                            }
                    }
                }
            }
            futures.add(future)
        }

        // 等待所有任务结束
        futures.forEach { it.get() }

        // 输出结果
        val elapsed = System.currentTimeMillis() - start
        logger.info(
            "process $nodeCollectionName finished, elapsed[$elapsed ms]" +
                "totalNodeCount[${totalCount.toLong()}, lived[${livedNodeCount.get()}]]"
        )
        return projectMetrics.values.toList()
    }

    private fun submitId(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        collectionName: String
    ): Boolean {
        val initId = nextId(null, 0, collectionName) ?: return false
        submitIdExecutor.execute {
            logger.info("submit id started.")
            var startId: String? = initId
            var preId: String? = null
            val start = System.currentTimeMillis()
            var submittedIdCount = 0L
            while (startId != null) {
                submittedIdCount++
                startIds.add(startId)
                preId = startId
                // 获取下一个id
                startId = nextId(startId, opJobProperties.batchSize, collectionName)
            }
            val elapsed = System.currentTimeMillis() - start
            logger.info("$submittedIdCount ids submitted, last id[$preId], elapsed[$elapsed]")
            lastId.set(preId)
        }
        return true
    }

    private fun nextId(
        startId: String? = null,
        skip: Int = opJobProperties.batchSize,
        collectionName: String
    ): String? {
        val criteria = Criteria()
        startId?.let { criteria.and(FIELD_NAME_ID).gte(ObjectId(it)) }
        val query = Query(criteria).with(Sort.by(FIELD_NAME_ID)).skip(skip.toLong())
        query.fields().include(FIELD_NAME_ID)
        return mongoTemplate.findOne(query, Map::class.java, collectionName)?.get(FIELD_NAME_ID)?.toString()
    }

    private fun collectionName(shardingIndex: Int): String = "${TABLE_PREFIX}$shardingIndex"

    @Synchronized
    private fun refreshExecutor() {
        if (executor == null) {
            executor = ThreadPoolExecutor(
                opJobProperties.threadPoolSize,
                opJobProperties.threadPoolSize,
                DEFAULT_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                LinkedBlockingQueue(DEFAULT_THREAD_POOL_QUEUE_CAPACITY),
                ThreadPoolExecutor.CallerRunsPolicy()
            )
            executor!!.allowCoreThreadTimeOut(true)
        } else if(executor!!.maximumPoolSize != opJobProperties.threadPoolSize) {
            executor!!.corePoolSize = opJobProperties.threadPoolSize
            executor!!.maximumPoolSize = opJobProperties.threadPoolSize
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val INFLUX_COLLECION = "repoInfo"
        private const val TABLE_PREFIX = "node_"
        private const val TOGIGABYTE = 1024 * 1024 * 1024
        private const val FIELD_NAME_ID = AbstractMongoDao.ID
        private const val FIELD_NAME_DELETED = "deleted"
        private const val DEFAULT_ID_QUEUE_SIZE = 10000
        private const val DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 60L
        private const val DEFAULT_THREAD_POOL_QUEUE_CAPACITY = 1000
    }
}
