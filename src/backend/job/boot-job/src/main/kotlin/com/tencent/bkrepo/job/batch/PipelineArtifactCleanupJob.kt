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
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.ID
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.PipelineArtifactCleanupJobProperties
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

/**
 * 流水线构件清理任务
 */
@Component
@EnableConfigurationProperties(PipelineArtifactCleanupJobProperties::class)
class PipelineArtifactCleanupJob(
    private val properties: PipelineArtifactCleanupJobProperties,
    private val mongoTemplate: MongoTemplate
) : BatchJob(properties) {

    private val executor = ThreadPoolExecutor(
        properties.concurrency,
        properties.concurrency,
        0,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(SHARDING_COUNT / properties.intervalDays),
        ThreadFactoryBuilder().setNameFormat("Pipeline-Cleanup-%d").build()
    )

    @Scheduled(cron = "0 */1 * * * ?") // 每天同步清理一次
    override fun start(): Boolean {
        return super.start()
    }

    override fun doStart(jobContext: JobContext) {
        getCollectionNames().forEach {
            executor.submit { cleanup(it) }
        }
    }

    private fun getCollectionNames(): List<String> {
        val batchSize = SHARDING_COUNT / properties.intervalDays
        val batch = LocalDate.now().dayOfYear % properties.intervalDays
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

        do {
            pipelineNodeList = getPipelineNodeList(collectionName)

        } while (pipelineNodeList.isNotEmpty())
        pipelineNodeList.forEach { pipelineNode ->
            getBuildFolder(collectionName, pipelineNode)?.let { buildNode ->
                deleteBeforeBuild(collectionName, buildNode)
            }
        }
    }

    private fun getPipelineNodeList(collectionName: String): List<Node> {
        val query = Query(
            where(Node::folder).isEqualTo(true)
                .and(Node::repoName).isEqualTo(PIPELINE)
                .and(Node::path).isEqualTo(PathUtils.ROOT)
                .and(Node::deleted).isEqualTo(null)
        ).with(Sort.by(Sort.Direction.ASC, ID))
        return mongoTemplate.find(query, collectionName)
    }

    private fun getBuildFolder(collectionName: String, pipelineNode: Node): Node? {
        val query = Query(
            where(Node::projectId).isEqualTo(pipelineNode.projectId)
                .and(Node::repoName).isEqualTo(pipelineNode.repoName)
                .and(Node::path).isEqualTo(pipelineNode.fullPath + PathUtils.UNIX_SEPARATOR)
                .and(Node::folder).isEqualTo(true)
                .and(Node::deleted).isEqualTo(null)
        ).with(Sort.by(Sort.Direction.DESC, ID)).skip(properties.reservedFrequency).limit(1)
        return mongoTemplate.findOne(query, collectionName)
    }

    private fun deleteBeforeBuild(collectionName: String, buildNode: Node) {
        val query = Query(
            where(Node::projectId).isEqualTo(buildNode.projectId)
                .and(Node::repoName).isEqualTo(buildNode.repoName)
                .and(Node::fullPath).regex("^${(buildNode.path)}")
                .and(Node::createdDate).lt(buildNode.createdDate)
                .and(Node::deleted).isEqualTo(null)
        )
        val update = Update.update(Node::deleted.name, null)
        val result = mongoTemplate.updateMulti(query, update, collectionName)
        logger.info("$buildNode, result: ${result.matchedCount}")
    }

    data class Node(
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