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

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.DeletedNodeCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.DeletedNodeCleanupJobProperties
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.PageRequest
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
    private val fileReferenceClient: FileReferenceClient,
    private val clusterProperties: ClusterProperties
) : MongoDbBatchJob<DeletedNodeCleanupJob.Repository, DeletedNodeCleanupJobContext>(properties) {

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val folder: Boolean,
        val sha256: String?,
        val deleted: LocalDateTime?,
        val clusterNames: List<String>?
    )

    data class Repository(
        val id: String,
        val projectId: String,
        val name: String,
        val credentialsKey: String?,
        val deleted: LocalDateTime?
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun createJobContext(): DeletedNodeCleanupJobContext {
        return DeletedNodeCleanupJobContext(
            expireDate = LocalDateTime.now().minusDays(properties.deletedNodeReserveDays),
        )
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_REPOSITORY)
    }

    override fun buildQuery(): Query {
        return Query()
    }

    override fun mapToEntity(row: Map<String, Any?>): Repository {
        return Repository(
            id = row[ID].toString(),
            projectId = row[PROJECT].toString(),
            name = row[Repository::name.name].toString(),
            credentialsKey = row[CREDENTIALS]?.toString(),
            deleted = TimeUtils.parseMongoDateTimeStr(row[DELETED_DATE].toString()),
        )
    }

    override fun entityClass(): Class<Repository> {
        return Repository::class.java
    }

    override fun run(row: Repository, collectionName: String, context: DeletedNodeCleanupJobContext) {
        val query = buildNodeQuery(row.projectId, row.name, context.expireDate)
        val nodeCollectionName = COLLECTION_NODE_PREFIX +
            MongoShardingUtils.shardingSequence(row.projectId, SHARDING_COUNT)
        while (true) {
            val deletedNodeList =
                mongoTemplate.find(query, Node::class.java, nodeCollectionName).takeIf { it.isNotEmpty() } ?: break
            logger.info("Retrieved [${deletedNodeList.size}] deleted records from ${row.projectId}/${row.name}")
            deletedNodeList.forEach { node ->
                cleanUpNode(row, node, nodeCollectionName)
                if (node.folder) {
                    context.folderCount.incrementAndGet()
                } else {
                    context.fileCount.incrementAndGet()
                }
            }
        }
        // 仓库被标记为已删除，且该仓库下不存在任何节点时，删除仓库
        if (row.deleted != null &&
            mongoTemplate.count(buildNodeQuery(row.projectId, row.name), nodeCollectionName) == 0L
        ) {
            val repoQuery = Query.query(Criteria.where(ID).isEqualTo(row.id))
            mongoTemplate.remove(repoQuery, collectionName)
            context.repoDeleteCount.incrementAndGet()
            logger.info("Clean up deleted repository[${row.projectId}/${row.name}] for no nodes remaining")
        }
    }

    private fun cleanUpNode(repo: Repository, node: Node, nodeCollectionName: String) {
        var fileReferenceChanged = false
        try {
            val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(node.id))
            mongoTemplate.remove(nodeQuery, nodeCollectionName)
            if (!node.folder
                && (node.clusterNames == null || node.clusterNames.contains(clusterProperties.self.name))
            ) {
                fileReferenceChanged = decrementFileReference(node, repo)
            }
        } catch (ignored: Exception) {
            logger.error("Clean up deleted node[$node] failed in collection[$nodeCollectionName].", ignored)
            if (fileReferenceChanged) {
                incrementFileReference(node, repo)
            }
        }
    }

    private fun buildNodeQuery(projectId: String, repoName: String, deletedBefore: LocalDateTime? = null): Query {
        val criteria = where(Node::projectId).isEqualTo(projectId)
            .and(Node::repoName).isEqualTo(repoName)
        deletedBefore?.let { criteria.and(Node::deleted).lt(it) }
        return Query.query(criteria).with(PageRequest.of(0, PAGE_SIZE))
    }

    private fun decrementFileReference(node: Node, repo: Repository): Boolean {
        if (node.sha256.isNullOrBlank() || node.sha256 == FAKE_SHA256) {
            return false
        }
        return fileReferenceClient.decrement(node.sha256, repo.credentialsKey).data!!
    }

    private fun incrementFileReference(node: Node, repo: Repository): Boolean {
        if (node.sha256.isNullOrBlank()) {
            return false
        }
        return fileReferenceClient.increment(node.sha256, repo.credentialsKey).data!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeletedNodeCleanupJob::class.java)
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COLLECTION_REPOSITORY = "repository"
        private const val PAGE_SIZE = 1000
    }
}
