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

import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.PipelineArtifactCleanupJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeCleanRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 流水线构件清理任务
 */
@Component
@EnableConfigurationProperties(PipelineArtifactCleanupJobProperties::class)
class PipelineArtifactCleanupJob(
    private val properties: PipelineArtifactCleanupJobProperties,
    private val nodeClient: NodeClient,
) : DefaultContextMongoDbJob<PipelineArtifactCleanupJob.Node>(properties) {
    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT)
            .map { "$COLLECTION_NAME_PREFIX$it" }
            .toList()
    }

    /**
     * 查询流水线/报告仓库的一级目录（流水线目录）
     *
     * 示例 /p-xxxxxx
     */
    override fun buildQuery(): Query {
        return Query(
            where(Node::folder.name).isEqualTo(true)
                .orOperator(
                    where(Node::repoName.name).isEqualTo(PIPELINE),
                    where(Node::repoName.name).isEqualTo(REPORT)
                )
                .and(Node::path.name).isEqualTo(PathUtils.ROOT)
                .and(Node::deleted.name).isEqualTo(null)
        ).with(Sort.by(Sort.Direction.ASC, Node::id.name))
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(row)
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        getEarliestBuildNode(collectionName, row)?.let { buildNode ->
            deleteBeforeBuild(buildNode)
        }
    }

    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(7)
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
            where(Node::projectId.name).isEqualTo(pipelineNode.projectId)
                .and(Node::repoName.name).isEqualTo(pipelineNode.repoName)
                .and(Node::path.name).isEqualTo(pipelineNode.fullPath + PathUtils.UNIX_SEPARATOR)
                .and(Node::folder.name).isEqualTo(true)
                .and(Node::deleted.name).isEqualTo(null)
        ).with(Sort.by(Sort.Direction.DESC, Node::id.name)).skip(properties.reservedFrequency - 1).limit(1)
        return mongoTemplate.findOne(query, collectionName)
    }

    /**
     * 删除流水线/报告仓库早于[getEarliestBuildNode]的构建文件
     */
    private fun deleteBeforeBuild(buildNode: Node) {
        try {
            val result = nodeClient.cleanNodes((NodeCleanRequest(
                projectId = buildNode.projectId,
                repoName = buildNode.repoName,
                path = buildNode.path,
                date = buildNode.createdDate,
                operator = SYSTEM_USER
            ))).data
            logger.info(
                "delete ${result?.deletedNumber} node " +
                    "in [${buildNode.projectId}/${buildNode.repoName}${buildNode.path}]"
            )
        } catch (e: NullPointerException) {
            logger.warn("clean nodes in [${buildNode.projectId}/${buildNode.repoName}${buildNode.path}] timeout")
        }
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
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Node::id.name].toString(), map[Node::projectId.name].toString(),
            map[Node::repoName.name].toString(), map[Node::path.name].toString(),
            map[Node::fullPath.name].toString(), map[Node::folder.name] as Boolean,
            TimeUtils.parseMongoDateTimeStr(map[Node::createdDate.name].toString())!!,
            map[Node::deleted.name]?.let { TimeUtils.parseMongoDateTimeStr(it.toString()) }
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineArtifactCleanupJob::class.java)
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
