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

package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.config.properties.NodeStatCompositeMongoDbBatchJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(NodeStatCompositeMongoDbBatchJobProperties::class)
class NodeStatCompositeMongoDbBatchJob(
    val properties: NodeStatCompositeMongoDbBatchJobProperties,
) : CompositeMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "node_$it" }.toList()
    }

    override fun buildQuery(): Query = Query(
        Criteria.where(DELETED_DATE).`is`(null)
            .and(FOLDER).`is`(false)
    )

    override fun mapToEntity(row: Map<String, Any?>): Node = Node(row)

    override fun entityClass(): KClass<Node> = Node::class

    override fun createChildJobs(): List<ChildMongoDbBatchJob<Node>> {
        return listOf()
    }

    /**
     * 最长加锁时间
     */
    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    data class Node(private val map: Map<String, Any?>) {
        // 需要通过@JvmField注解将Kotlin backing-field直接作为Java field使用，MongoDbBatchJob中才能解析出需要查询的字段
        @JvmField
        val id: String

        @JvmField
        val folder: Boolean

        @JvmField
        val path: String

        @JvmField
        val fullPath: String

        @JvmField
        val name: String

        @JvmField
        val size: Long

        @JvmField
        val projectId: String

        @JvmField
        val repoName: String

        init {
            id = map[Node::id.name] as String
            folder = map[Node::folder.name] as Boolean
            path = map[Node::path.name] as String
            fullPath = map[Node::fullPath.name] as String
            name = map[Node::name.name] as String
            size = map[Node::size.name].toString().toLong()
            projectId = map[Node::projectId.name] as String
            repoName = map[Node::repoName.name] as String
        }
    }
}
