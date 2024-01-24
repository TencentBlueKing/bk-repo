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

import com.tencent.bkrepo.common.stream.constant.BinderType
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.NodeReport2BkbaseJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/**
 * 导出node表至数据平台
 */
@Component
@EnableConfigurationProperties(NodeReport2BkbaseJobProperties::class)
class NodeReport2BkbaseJob(
    val properties: NodeReport2BkbaseJobProperties,
    val messageSupplier: MessageSupplier
) : DefaultContextMongoDbJob<NodeReport2BkbaseJob.Node>(properties) {


    override fun collectionNames(): List<String> {
        return (properties.startCollectionNum until properties.endCollectionsNum)
            .map { "${COLLECTION_NAME_PREFIX}$it" }
            .toList()
    }

    override fun buildQuery(): Query {
        return Query(where(Node::folder).isEqualTo(false)).with(Sort.by(Sort.Direction.ASC, Node::id.name))
    }

    override fun mapToEntity(map: Map<String, Any?>): Node {
        return Node(
            id = map[Node::id.name].toString(),
            createdBy = map[Node::createdBy.name].toString(),
            createdDate = TimeUtils.parseMongoDateTimeStr(map[Node::createdDate.name].toString())!!,
            lastModifiedBy = map[Node::lastModifiedBy.name].toString(),
            lastModifiedDate = TimeUtils.parseMongoDateTimeStr(map[Node::lastModifiedDate.name].toString())!!,
            lastAccessDate = TimeUtils.parseMongoDateTimeStr(map[Node::lastAccessDate.name].toString()),
            folder = map[Node::folder.name].toString().toBoolean(),
            path = map[Node::path.name].toString(),
            name = map[Node::name.name].toString(),
            fullPath = map[Node::fullPath.name].toString(),
            size = map[Node::size.name]?.toString()?.toLong() ?: 0L,
            expireDate = TimeUtils.parseMongoDateTimeStr(map[Node::expireDate.name].toString()),
            sha256 = map[Node::sha256.name]?.toString(),
            md5 = map[Node::md5.name]?.toString(),
            deleted = TimeUtils.parseMongoDateTimeStr(map[Node::deleted.name].toString()),
            copyFromCredentialsKey = map[Node::copyFromCredentialsKey.name].toString(),
            copyIntoCredentialsKey = map[Node::copyIntoCredentialsKey.name].toString(),
            metadata = map[Node::metadata.name] as? List<Map<String, String>>,
            projectId = map[Node::projectId.name].toString(),
            repoName = map[Node::repoName.name].toString()
        )
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        // 数据平台支持去重，此处不需要处理重复发送的情况
        if (row.createdDate.isBefore(
                LocalDateTime.parse(properties.endDateTime, DateTimeFormatter.ISO_DATE_TIME)
            ) && row.deleted == null
        ) {
            messageSupplier.delegateToSupplier(row, topic = TOPIC, binderType = BinderType.KAFKA)
        }
    }

    data class Node(
        val id: String,
        val createdBy: String,
        val createdDate: LocalDateTime,
        val lastModifiedBy: String,
        val lastModifiedDate: LocalDateTime,
        val lastAccessDate: LocalDateTime?,

        val folder: Boolean,
        val path: String,
        val name: String,
        val fullPath: String,
        val size: Long,
        val expireDate: LocalDateTime?,
        val sha256: String?,
        val md5: String?,
        val deleted: LocalDateTime?,
        val copyFromCredentialsKey: String?,
        val copyIntoCredentialsKey: String?,
        val metadata: List<Map<String, String>>?,

        val projectId: String,
        val repoName: String
    )

    companion object {
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val TOPIC = "bkbase-bkrepo-artifact-node-created"
    }
}
