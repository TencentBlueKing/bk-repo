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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.DeletedNodeCleanupJobProperties
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 清理被标记为删除的node，同时减少文件引用
 */
@Component("JobServiceDeletedNodeCleanupJob")
@EnableConfigurationProperties(DeletedNodeCleanupJobProperties::class)
class DeletedNodeCleanupJob(
    private val properties: DeletedNodeCleanupJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val fileReferenceClient: FileReferenceClient
) : DefaultContextMongoDbJob<DeletedNodeCleanupJob.Node>(properties) {

    private val repoCache = CacheBuilder.newBuilder()
        .maximumSize(SHARDING_COUNT.toLong())
        .build(object : CacheLoader<Pair<String, String>, Repository?>() {
            override fun load(key: Pair<String, String>): Repository? = queryRepository(key)
        })


    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val folder: Boolean,
        val sha256: String?,
        val deleted: LocalDateTime?
    )

    data class Repository(
        val projectId: String,
        val name: String,
        val credentialsKey: String?
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        val collectionNames = mutableListOf<String>()
        for (i in 0 until SHARDING_COUNT) {
            collectionNames.add("$COLLECTION_NAME_PREFIX$i")
        }
        return collectionNames
    }

    override fun buildQuery(): Query {
        val expireDate = LocalDateTime.now().minusDays(properties.deletedNodeReserveDays)
        return Query.query(where(Node::deleted).lt(expireDate))
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(
            id = row[Node::id.name].toString(),
            projectId = row[Node::projectId.name].toString(),
            repoName = row[Node::repoName.name].toString(),
            folder = row[Node::folder.name].toString().toBoolean(),
            sha256 = row[Node::sha256.name]?.toString(),
            deleted = TimeUtils.parseMongoDateTimeStr(row[Node::deleted.name].toString())
        )
    }

    override fun entityClass(): Class<Node> {
        return Node::class.java
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        var fileReferenceChanged = false
        try {
            val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(row.id))
            mongoTemplate.remove(nodeQuery, collectionName)
            if (!row.folder) {
                fileReferenceChanged = decrementFileReference(row)
            }
        } catch (ignored: Exception) {
            logger.error("Clean up deleted node[$row] failed in collection[$collectionName].", ignored)
            if (fileReferenceChanged) {
                incrementFileReference(row)
            }
        }
    }

    private fun decrementFileReference(node: Node): Boolean {
        if (node.sha256.isNullOrBlank()) {
            return false
        }
        val credentialsKey = repoCache.get(Pair(node.projectId, node.repoName))?.credentialsKey
        return fileReferenceClient.decrement(node.sha256, credentialsKey).data!!
    }

    private fun incrementFileReference(node: Node): Boolean {
        if (node.sha256.isNullOrBlank()) {
            return false
        }
        val credentialsKey = repoCache.get(Pair(node.projectId, node.repoName))?.credentialsKey
        return fileReferenceClient.increment(node.sha256, credentialsKey).data!!
    }

    private fun queryRepository(cacheKey: Pair<String, String>): Repository? {
        val (projectId, repoName) = cacheKey
        val query = Query.query(
            where(Repository::projectId).isEqualTo(projectId)
                .and(Repository::name).isEqualTo(repoName)
        )
        return mongoTemplate.findOne(query, Repository::class.java, COLLECTION_REPOSITORY)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(DeletedNodeCleanupJob::class.java)
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val COLLECTION_REPOSITORY = "repository"
    }
}
