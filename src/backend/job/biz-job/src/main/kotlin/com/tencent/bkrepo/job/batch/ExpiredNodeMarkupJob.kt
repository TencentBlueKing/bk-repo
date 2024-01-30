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

import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.ExpiredNodeMarkupJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 标记已过期的节点为已删除
 */
@Component
@EnableConfigurationProperties(ExpiredNodeMarkupJobProperties::class)
class ExpiredNodeMarkupJob(
    properties: ExpiredNodeMarkupJobProperties,
    private val nodeClient: NodeClient
) : DefaultContextMongoDbJob<ExpiredNodeMarkupJob.Node>(properties) {

    data class Node(
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val expireDate: LocalDateTime,
        val deleted: LocalDateTime?
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun collectionNames(): List<String> {
        val collectionNames = mutableListOf<String>()
        for (index in 0 until SHARDING_COUNT) {
            collectionNames.add("$COLLECTION_NAME_PREFIX$index")
        }
        return collectionNames
    }

    override fun buildQuery(): Query {
        return Query.query(
            where(Node::expireDate).lt(LocalDateTime.now())
                .and(Node::deleted).isEqualTo(null)
        )
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(
            row[Node::projectId.name].toString(),
            row[Node::repoName.name].toString(),
            row[Node::fullPath.name].toString(),
            TimeUtils.parseMongoDateTimeStr(row[Node::expireDate.name].toString())!!,
            TimeUtils.parseMongoDateTimeStr(row[Node::deleted.name].toString())
        )
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        try {
            nodeClient.deleteNode(NodeDeleteRequest(row.projectId, row.repoName, row.fullPath, SYSTEM_USER))
        } catch (e: Exception) {
            logger.warn("delete expired node[$row] failed: $e")
        }
    }

    companion object {
        private const val COLLECTION_NAME_PREFIX = "node_"
        private val logger = LoggerFactory.getLogger(ExpiredNodeMarkupJob::class.java)
    }
}
