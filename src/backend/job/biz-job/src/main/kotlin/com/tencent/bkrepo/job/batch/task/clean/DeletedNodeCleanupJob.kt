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

package com.tencent.bkrepo.job.batch.task.clean

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.UncheckedExecutionException
import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.DeletedNodeCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.DeletedNodeCleanupJobProperties
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
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
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 清理被标记为删除的node，同时减少文件引用
 */
@Component("JobServiceDeletedNodeCleanupJob")
@EnableConfigurationProperties(DeletedNodeCleanupJobProperties::class)
class DeletedNodeCleanupJob(
    private val properties: DeletedNodeCleanupJobProperties,
    private val clusterProperties: ClusterProperties,
    private val migrateRepoStorageService: MigrateRepoStorageService,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val separationTaskService: SeparationTaskService,
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

    data class Repository(
        val id: String,
        val projectId: String,
        val name: String,
        val credentialsKey: String?
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(28)

    override fun createJobContext(): DeletedNodeCleanupJobContext {
        return DeletedNodeCleanupJobContext()
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_NODE_PREFIX$it" }.toList()
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
        // 仓库正在迁移时删除node会导致迁移任务分页查询数据重复或缺失，需要等迁移完后再执行清理
        if (migrateRepoStorageService.migrating(row.projectId, row.repoName)) {
            logger.info("repo[${row.projectId}/${row.repoName}] storage was migrating, skip clean node[${row.sha256}]")
            return
        }
        if (separationTaskService.repoSeparationCheck(row.projectId, row.repoName)) {
            logger.info("repo[${row.projectId}/${row.repoName}] was doing separation, skip clean node[${row.sha256}]")
            return
        }

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
            if (node.sha256.isNullOrEmpty() || node.sha256 == FAKE_SHA256) return
            try {
                val credentialsKey = getCredentialsKey(node.projectId, node.repoName)
                val deletedDays = node.deleted?.let { Duration.between(it, LocalDateTime.now()).toDays() } ?: 0
                val keepRefLostNode = deletedDays < properties.keepRefLostNodeDays
                // 需要保留Node用于排查问题时不补偿创建引用，避免引用创建后node记录可以被正常删除
                val createIfNotExists = !keepRefLostNode
                if (!decrementFileReferences(node.sha256, credentialsKey, createIfNotExists)) {
                    logger.warn("Clean up node fail collection[$collectionName], node[$node]")
                    return
                }
            } catch (e: UncheckedExecutionException) {
                require(e.cause is RepoNotFoundException)
                logger.warn("repo ${node.projectId}|${node.repoName} was deleted!")
                handleNodeWithUnknownRepo(node.sha256)
            }
            result = mongoTemplate.remove(query, collectionName)
        } catch (ignored: Exception) {
            logger.error("Clean up deleted node[$node] failed in collection[$collectionName].", ignored)
        }

        context.fileCount.addAndGet(result?.deletedCount ?: 0)
    }

    private fun decrementFileReferences(sha256: String, credentialsKey: String?, createIfNotExists: Boolean): Boolean {
        val collectionName = COLLECTION_FILE_REFERENCE + MongoShardingUtils.shardingSequence(sha256, SHARDING_COUNT)
        val criteria = buildCriteria(sha256, credentialsKey)
        criteria.and(FileReference::count.name).gt(0)
        val query = Query(criteria)
        val update = Update().apply { inc(FileReference::count.name, -1) }
        val result = mongoTemplate.updateFirst(query, update, collectionName)

        if (result.modifiedCount == 1L) {
            logger.info("Decrement references of file [$sha256] on credentialsKey [$credentialsKey].")
            return true
        }

        val newQuery = Query(buildCriteria(sha256, credentialsKey))
        mongoTemplate.findOne<FileReference>(newQuery, collectionName) ?: run {
            logger.error("Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]")
            if (createIfNotExists) {
                /* 早期FileReferenceCleanupJob在最终存储不存在时，不会判断对应的node是否存在而是直接删除引用，
                 * 导致出现node存在而引用不存在的情况，此处为这些引用缺失的数据补偿创建引用以清理对应的node及存储
                 */
                mongoTemplate.upsert(newQuery, Update().inc(FileReference::count.name, 0), collectionName)
                return true
            }
            return false
        }

        logger.error(
            "Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]: " +
                "reference count is 0."
        )
        return false
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
        .build(CacheLoader.from { key -> Optional.ofNullable(loadCredentialsKey(key!!)) })


    private fun loadCredentialsKey(repositoryId: RepositoryId): String? {
        val repo = getRepoInfo(repositoryId.projectId, repositoryId.repoName)
            ?: throw RepoNotFoundException("${repositoryId.projectId}/${repositoryId.repoName}")
        return repo.credentialsKey
    }

    private fun getRepoInfo(projectId: String, repoName: String): Repository? {
        val query = Query(Criteria.where(PROJECT).isEqualTo(projectId).and(NAME).isEqualTo(repoName))
        return mongoTemplate.findOne(query, Repository::class.java)
    }

    private fun handleNodeWithUnknownRepo(sha256: String) {
        val credentials = storageCredentialsClient.list().data
        val defaultCredentials = storageCredentialsClient.findByKey().data
        if (credentials.isNullOrEmpty() && defaultCredentials == null) return
        val keySet = mutableSetOf<String?>()
        if (!credentials.isNullOrEmpty()) {
            keySet.addAll(credentials.map { it.key })
        }
        if (defaultCredentials != null) {
            keySet.add(defaultCredentials.key)
        }
        keySet.forEach {
            // 由于不确定node在哪个存储，此处无法确定为丢失引用的node创建哪个存储的引用，因此引用丢失时不补偿创建引用
            // StorageReconcileJob中会为缺少引用的存储文件补偿创建引用
            decrementFileReferences(sha256, it, false)
        }
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
