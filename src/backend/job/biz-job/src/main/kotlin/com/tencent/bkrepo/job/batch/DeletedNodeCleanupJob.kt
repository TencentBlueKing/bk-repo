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
import com.google.common.cache.LoadingCache
import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.DeletedNodeCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.DeletedNodeCleanupJobProperties
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * 清理被标记为删除的node，同时减少文件引用
 */
@Component("JobServiceDeletedNodeCleanupJob")
@EnableConfigurationProperties(DeletedNodeCleanupJobProperties::class)
class DeletedNodeCleanupJob(
    private val properties: DeletedNodeCleanupJobProperties,
    private val clusterProperties: ClusterProperties,
    private val repositoryClient: RepositoryClient
) : DefaultContextMongoDbJob<DeletedNodeCleanupJob.Node>(properties) {

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val folder: Boolean,
        val sha256: String?,
        val deleted: LocalDateTime?,
        val clusterNames: List<String>?
    )

    data class FileReference(
        val sha256: String,
        val credentialsKey: String?,
        val count: String
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(28)

    override fun createJobContext(): DeletedNodeCleanupJobContext {
        return DeletedNodeCleanupJobContext()
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "${COLLECTION_NODE_PREFIX}$it" }.toList()
    }

    override fun buildQuery(): Query {
        val expireDate = LocalDateTime.now().minusDays(properties.deletedNodeReserveDays)
        return Query(Criteria.where(Node::deleted.name).lt(expireDate))
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(
            id = row[ID].toString(),
            projectId = row[PROJECT].toString(),
            repoName = row[REPO].toString(),
            folder = row[Node::folder.name] as Boolean,
            sha256 = row[Node::sha256.name] as String?,
            deleted = TimeUtils.parseMongoDateTimeStr(row[DELETED_DATE].toString()),
            clusterNames = row[Node::clusterNames.name] as List<String>?
        )
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }


    override fun run(row: Node, collectionName: String, context: JobContext) {
        require(context is DeletedNodeCleanupJobContext)
        if (row.folder) {
            cleanupFolderNode(context, row.id, collectionName)
        } else {
            cleanUpFileNode(context, row, collectionName)
        }
    }

    private fun cleanupFolderNode(
        context: DeletedNodeCleanupJobContext,
        id: String,
        collectionName: String
    ) {
        val query = Query.query(Criteria.where(ID).isEqualTo(id))
        val result = mongoTemplate.remove(query, collectionName)
        context.folderCount.addAndGet(result.deletedCount)
    }

    private fun cleanUpFileNode(
        context: DeletedNodeCleanupJobContext,
        node: Node,
        collectionName: String
    ) {
        if (!node.clusterNames.isNullOrEmpty() && !node.clusterNames.contains(clusterProperties.self.name)) return
        val query = Query.query(Criteria.where(ID).isEqualTo(node.id))
        var result: DeleteResult? = null
        try {
            result = mongoTemplate.remove(query, collectionName)
            if (node.sha256.isNullOrEmpty() || node.sha256 == FAKE_SHA256) return
            val credentialsKey = getCredentialsKey(node.projectId, node.repoName)
            decrementFileReferences(node.sha256, credentialsKey)
        } catch (ignored: Exception) {
            logger.error("Clean up deleted node[$node] failed in collection[$collectionName].", ignored)
        }

        context.fileCount.addAndGet(result?.deletedCount ?: 0)
    }

    private fun decrementFileReferences(sha256: String, credentialsKey: String?) {
        val collectionName = COLLECTION_FILE_REFERENCE + MongoShardingUtils.shardingSequence(sha256, SHARDING_COUNT)
        val criteria = buildCriteria(sha256, credentialsKey)
        criteria.and(FileReference::count.name).gt(0)
        val query = Query(criteria)
        val update = Update().apply { inc(FileReference::count.name, -1) }
        val result = mongoTemplate.updateFirst(query, update, collectionName)

        if (result.modifiedCount == 1L) {
            logger.info("Decrement references of file [$sha256] on credentialsKey [$credentialsKey].")
            return
        }

        val newQuery = Query(buildCriteria(sha256, credentialsKey))
        mongoTemplate.findOne<FileReference>(newQuery, collectionName) ?: run {
            logger.error("Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]")
            return
        }

        logger.error(
            "Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]: " +
                "reference count is 0."
        )
    }

    private fun buildCriteria(
        it: String,
        credentialsKey: String?
    ): Criteria {
        val criteria = Criteria.where(FileReference::sha256.name).`is`(it)
        criteria.and(FileReference::credentialsKey.name).`is`(credentialsKey)
        return criteria
    }

    private fun getCredentialsKey(projectId: String, repoName: String): String? {
        return credentialsKeyCache.get(RepositoryId(projectId, repoName)).orElse(null)
    }

    private val credentialsKeyCache: LoadingCache<RepositoryId, Optional<String>> = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build(CacheLoader.from { key ->  Optional.ofNullable(loadcredentialsKey(key!!)) })


    private fun loadcredentialsKey(repositoryId: RepositoryId): String? {
        val repo = repositoryClient.getRepoInfo(repositoryId.projectId, repositoryId.repoName).data
            ?: throw RepoNotFoundException("${repositoryId.projectId}/${repositoryId.repoName}")
        return repo.storageCredentialsKey
    }

    data class RepositoryId(val projectId: String, val repoName: String) {
        override fun toString(): String {
            return StringBuilder(projectId).append(CharPool.SLASH).append(repoName).toString()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeletedNodeCleanupJob::class.java)
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COLLECTION_FILE_REFERENCE = "file_reference_"
    }
}
