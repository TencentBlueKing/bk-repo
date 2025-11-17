/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.archive

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.IdleNodeArchiveJobProperties
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 空闲节点归档任务
 * 将长期未被访问的节点进行归档，具体步骤如下：
 * 1. 查找长期未访问的未归档节点
 * 2. 查看是否已经存在归档任务，如果有则跳过此次归档
 * 3. 查看文件引用数，如果引用数为1，则直接进行归档任务，否则检查引用是否在存活时间内被使用
 * 4. 如果未被使用，则进行归档。
 * */
@Component
class IdleNodeArchiveJob(
    private val properties: IdleNodeArchiveJobProperties,
    private val archiveClient: ArchiveClient,
    private val fileReferenceService: FileReferenceService,
    private val migrateRepoStorageService: MigrateRepoStorageService,
    private val storageService: StorageService,
) : MongoDbBatchJob<IdleNodeArchiveJob.Node, NodeContext>(properties) {
    private var lastCutoffTime: LocalDateTime? = null
    private var tempCutoffTime: LocalDateTime? = null
    private var refreshCount = INITIAL_REFRESH_COUNT
    private val nodeUseInfoCache = ConcurrentHashMap<String, Boolean>()

    override fun collectionNames(): List<String> {
        val collectionNames = mutableListOf<String>()
        if (properties.projectArchiveCredentialsKeys.isNotEmpty()) {
            properties.projectArchiveCredentialsKeys.keys.forEach {
                val index = HashShardingUtils.shardingSequenceFor(it, SHARDING_COUNT)
                collectionNames.add("$COLLECTION_NAME_PREFIX$index")
            }
        } else {
            (0 until SHARDING_COUNT).forEach { collectionNames.add("$COLLECTION_NAME_PREFIX$it") }
        }
        return collectionNames
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun doStart0(jobContext: NodeContext) {
        if (properties.projectArchiveCredentialsKeys.isEmpty()) {
            logger.info("projectArchiveCredentialsKeys is empty, skip archive job")
            return
        }
        super.doStart0(jobContext)
        // 由于新的文件可能会被删除，所以旧文件数据的引用会被改变，所以需要重新扫描旧文件引用。
        if (refreshCount-- < 0) {
            lastCutoffTime = null
            refreshCount = INITIAL_REFRESH_COUNT
        } else {
            lastCutoffTime = tempCutoffTime
        }
        nodeUseInfoCache.clear()
    }

    override fun buildQuery(): Query {
        val now = LocalDateTime.now()
        val cutoffTime = now.minus(Duration.ofDays(properties.days.toLong()))
        tempCutoffTime = cutoffTime
        return Query.query(
            Criteria.where("folder").isEqualTo(false)
                .and("deleted").isEqualTo(null)
                .and("sha256").ne(FAKE_SHA256)
                .and("archived").ne(true)
                .and("compressed").ne(true)
                .and("size").gt(properties.fileSizeThreshold.toBytes())
                .apply {
                    if (properties.projectArchiveCredentialsKeys.isNotEmpty()) {
                        and("projectId").inValues(properties.projectArchiveCredentialsKeys.keys)
                    }
                    if (lastCutoffTime == null) {
                        // 首次查询
                        orOperator(
                            Criteria.where("lastAccessDate").isEqualTo(null),
                            Criteria.where("lastAccessDate").lt(cutoffTime),
                        )
                    } else {
                        and("lastAccessDate").gte(lastCutoffTime!!).lt(cutoffTime)
                    }
                },
        )
    }

    override fun run(row: Node, collectionName: String, context: NodeContext) {
        val archiveCredentialsKey = properties.projectArchiveCredentialsKeys[row.projectId]
        val storageClass = properties.storageClass
        val days = properties.days
        logger.info("Start to archive $row, storageClass: $storageClass, collectionName: $collectionName, days: $days")
        archiveNode(row, context, storageClass, archiveCredentialsKey)
    }

    fun archiveNode(
        row: Node,
        context: NodeContext,
        storageClass: ArchiveStorageClass,
        archiveCredentialsKey: String?,
    ) {
        val sha256 = row.sha256
        val projectId = row.projectId
        val repoName = row.repoName
        val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
        val migrating = migrateRepoStorageService.migrating(projectId, repoName)
        if (migrating && !storageService.exist(sha256, repo.storageCredentials)) {
            logger.info("repo[$projectId/$repoName] is migrating, skip unmigrated node[${row.fullPath}][$sha256]")
            return
        }
        val credentialsKey = repo.storageCredentials?.key
        if (properties.ignoreStorageCredentialsKeys.contains(credentialsKey) ||
            properties.ignoreRepoType.contains(repo.type.name)
        ) {
            logger.info("Skip $row#${repo.type.name} on $credentialsKey.")
            return
        }
        if (nodeUseInfoCache[sha256] == true) {
            logger.info("Find it[$row] in use by cache,skip archive.")
            return
        }
        val count = fileReferenceService.count(sha256, credentialsKey)
        if (count == 1L) {
            // 快速归档
            createArchiveFile(credentialsKey, context, row, storageClass, archiveCredentialsKey)
        } else {
            synchronized(sha256.intern()) {
                slowArchive(row, credentialsKey, context, storageClass, archiveCredentialsKey)
            }
        }
    }

    private fun slowArchive(
        row: Node,
        credentialsKey: String?,
        context: NodeContext,
        storageClass: ArchiveStorageClass,
        archiveCredentialsKey: String?,
    ) {
        with(row) {
            val inUse = nodeUseInfoCache[sha256] ?: checkUse(sha256, row.projectId)
            if (inUse) {
                // 只需要缓存被使用的情况，这可以避免sha256被重复搜索。当sha256未被使用时，它会创建一条归档记录，所以无需缓存。
                nodeUseInfoCache[sha256] = true
            } else {
                createArchiveFile(credentialsKey, context, row, storageClass, archiveCredentialsKey)
            }
        }
    }

    private fun createArchiveFile(
        credentialsKey: String?,
        context: NodeContext,
        row: Node,
        storageClass: ArchiveStorageClass,
        archiveCredentialsKey: String?,
    ) {
        with(row) {
            val createArchiveFileRequest = CreateArchiveFileRequest(
                sha256 = sha256,
                size = size,
                storageCredentialsKey = credentialsKey,
                archiveCredentialsKey = archiveCredentialsKey,
                storageClass = storageClass,
                operator = SYSTEM_USER,
            )
            archiveClient.archive(createArchiveFileRequest)
            context.count.incrementAndGet()
            context.size.addAndGet(row.size)
            logger.info("Success to archive node [$row],lat:$lastAccessDate.")
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(
            id = row[Node::id.name].toString(),
            projectId = row[Node::projectId.name].toString(),
            repoName = row[Node::repoName.name].toString(),
            fullPath = row[Node::fullPath.name].toString(),
            sha256 = row[Node::sha256.name].toString(),
            size = row[Node::size.name].toString().toLong(),
            lastAccessDate = TimeUtils.parseMongoDateTimeStr(row[Node::lastAccessDate.name].toString()),
        )
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val sha256: String,
        val size: Long,
        var lastAccessDate: LocalDateTime? = null,
    ) {
        override fun toString(): String {
            return "$projectId/$repoName$fullPath($sha256,$lastAccessDate)"
        }
    }

    private fun checkUse(sha256: String, projectId: String): Boolean {
        /*
        * 满足以下条件之一，则不进行归档
        * 1. 其他项目存在相同sha256的节点。（跨项目的文件会无法归档）
        * */
        for (i in 0 until SHARDING_COUNT) {
            val collectionName = COLLECTION_NAME_PREFIX.plus(i)
            val query = Query.query(
                Criteria.where("sha256").isEqualTo(sha256)
                    .and("deleted").isEqualTo(null)
                    .and("projectId").ne(projectId)
            )
            val existNode = mongoTemplate.findOne(query, Node::class.java, collectionName)
            if (existNode != null) {
                logger.info("Find in use $existNode.")
                return true
            }
        }
        return false
    }

    companion object {
        const val COLLECTION_NAME_PREFIX = "node_"
        private const val INITIAL_REFRESH_COUNT = 3
        private val logger = LoggerFactory.getLogger(IdleNodeArchiveJob::class.java)
    }
}
