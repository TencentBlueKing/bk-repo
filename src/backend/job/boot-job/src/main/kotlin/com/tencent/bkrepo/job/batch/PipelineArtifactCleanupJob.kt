/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.MIN_OBJECT_ID
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.PipelineArtifactCleanupJobProperties
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/**
 * 流水线构件清理任务
 */
@Component
@EnableConfigurationProperties(PipelineArtifactCleanupJobProperties::class)
class PipelineArtifactCleanupJob(
    private val properties: PipelineArtifactCleanupJobProperties,
    private val mongoTemplate: MongoTemplate
) : DefaultContextJob(properties) {

    private val executor = ThreadPoolExecutor(
        properties.concurrency,
        properties.concurrency,
        0,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(properties.batchSize),
        ThreadFactoryBuilder().setNameFormat("Pipeline-Artifact-Cleanup-%d").build()
    )

    private val statisticMap = mutableMapOf<String, Long>()

    @Scheduled(cron = "0 20 1 * * ?")
    override fun start(): Boolean {
        return super.start()
    }

    override fun doStart0(jobContext: JobContext) {
        val futureList = getCollectionNames().map {
            jobContext.total.getAndIncrement()
            statisticMap[it] = 0L
            executor.submit { cleanup(it) }
        }
        futureList.forEach {
            it.get()
            jobContext.success.getAndIncrement()
        }
    }

    private fun getCollectionNames(): List<String> {
        val batchSize = tableSizeFor(properties.batchSize)
        val batch = LocalDate.now().dayOfYear % (SHARDING_COUNT / properties.batchSize)
        val start = (batch * batchSize) % SHARDING_COUNT
        val end = (start + batchSize) % SHARDING_COUNT

        val collectionNames = mutableListOf<String>()
        var index = start
        do {
            collectionNames.add("$COLLECTION_NAME_PREFIX$index")
            index = (index + 1) % SHARDING_COUNT
        } while (index != end)
        return collectionNames
    }

    private fun cleanup(collectionName: String) {
        var pipelineNodeList: List<Node>
        var lastId = MIN_OBJECT_ID
        val time = measureNanoTime {
            do {
                pipelineNodeList = getPipelineNodeList(collectionName, lastId)
                if (pipelineNodeList.isNotEmpty()) {
                    lastId = pipelineNodeList.last().id
                    pipelineNodeList.forEach { pipelineNode ->
                        getEarliestBuildNode(collectionName, pipelineNode)?.let { buildNode ->
                            deleteBeforeBuild(collectionName, buildNode)
                        }
                        Thread.sleep(TimeUnit.SECONDS.toMillis(2))
                    }
                }
            } while (pipelineNodeList.isNotEmpty())
        }
        logger.info("$collectionName clean up ${statisticMap[collectionName]} node, cost ${HumanReadable.time(time)}")
    }

    /**
     * 查询流水线/报告仓库的一级目录（流水线目录）
     *
     * 示例 /p-xxxxxx
     */
    private fun getPipelineNodeList(collectionName: String, lastId: String): List<Node> {
        val query = Query(
            where(Node::folder).isEqualTo(true)
                .orOperator(
                    where(Node::repoName).isEqualTo(PIPELINE),
                    where(Node::repoName).isEqualTo(REPORT)
                )
                .and(Node::path).isEqualTo(PathUtils.ROOT)
                .and(Node::deleted).isEqualTo(null)
                .and(Node::id).gt(ObjectId(lastId))
        ).with(Sort.by(Sort.Direction.ASC, Node::id.name)).limit(1000)
        return mongoTemplate.find(query, collectionName)
    }

    /**
     * 查询流水线/报告仓库的ID降序第[PipelineArtifactCleanupJobProperties.reservedFrequency]个二级目录（构建目录）
     *
     * 即最近的[reservedFrequency]次构建的最早一次
     *
     * 示例 /p-xxxxx/b-xxxxx
     */
    private fun getEarliestBuildNode(collectionName: String, pipelineNode: Node): Node? {
        val query = Query(
            where(Node::projectId).isEqualTo(pipelineNode.projectId)
                .and(Node::repoName).isEqualTo(pipelineNode.repoName)
                .and(Node::path).isEqualTo(pipelineNode.fullPath + PathUtils.UNIX_SEPARATOR)
                .and(Node::folder).isEqualTo(true)
                .and(Node::deleted).isEqualTo(null)
        ).with(Sort.by(Sort.Direction.DESC, Node::id.name)).skip(properties.reservedFrequency-1).limit(1)
        return mongoTemplate.findOne(query, collectionName)
    }

    /**
     * 删除流水线/报告仓库早于[getEarliestBuildNode]的构建文件
     */
    private fun deleteBeforeBuild(collectionName: String, buildNode: Node) {
        val query = Query(
            where(Node::projectId).isEqualTo(buildNode.projectId)
                .and(Node::repoName).isEqualTo(buildNode.repoName)
                .and(Node::fullPath).regex("^${(buildNode.path)}")
                .and(Node::createdDate).lt(buildNode.createdDate)
                .and(Node::deleted).isEqualTo(null)
        )
        val update = Update.update(Node::deleted.name, LocalDateTime.now())
        val result = mongoTemplate.updateMulti(query, update, collectionName)
        statisticMap[collectionName] = statisticMap[collectionName]!! + result.modifiedCount
    }

    private fun tableSizeFor(batchSize: Int): Int {
        var n: Int = batchSize - 1
        n = n or (n ushr 1)
        n = n or (n ushr 2)
        n = n or (n ushr 4)
        n = n or (n ushr 8)
        n = n or (n ushr 16)
        return if (n < 0) 1 else if (n >= SHARDING_COUNT) SHARDING_COUNT else n + 1
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val path: String,
        val fullPath: String,
        val folder: Boolean,
        val createdDate: LocalDateTime,
        val deleted: LocalDateTime? = null
    )

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineArtifactCleanupJob::class.java)
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
