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

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.ARCHIVE_FILE_COLLECTION
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.ArchivedNodeCompleteJobProperties
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 归档节点完成任务
 * 对文件数据已经归档的节点，进行归档处理。具体步骤如下：
 * 1. 找到文件数据对应的待归档节点，如果没有跳至4
 * 2. 检查是否有新的节点使用该文件数据，如果有，则删除归档数据，停止归档。
 * 3. 把相应的节点进行归档
 * 4. 完成文件数据归档。
 * */
@Component
class ArchivedNodeCompleteJob(
    val properties: ArchivedNodeCompleteJobProperties,
    private val archiveClient: ArchiveClient,
    private val nodeService: NodeService,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
) : MongoDbBatchJob<ArchivedNodeRestoreJob.ArchiveFile, NodeContext>(properties) {

    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf(ARCHIVE_FILE_COLLECTION)
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(ArchiveStatus.ARCHIVED))
    }

    override fun run(row: ArchivedNodeRestoreJob.ArchiveFile, collectionName: String, context: NodeContext) {
        with(row) {
            val now = LocalDateTime.now()
            var shouldDeleteStorage = true
            val nodes = listNode(sha256, storageCredentialsKey)
            for (node in nodes) {
                val accessInterval = node.lastAccessDate?.let { Duration.between(it, now) }
                if (accessInterval != null && accessInterval < properties.minAccessInterval) {
                    logger.info("node[$node] was accessed recently, skip mark as archived")
                    // 存在开始归档后又被访问的同sha256制品保留原存储，此时可能导致冗余存储
                    shouldDeleteStorage = false
                    continue
                }
                archiveNode(node.projectId, node.repoName, node.fullPath)
                context.count.incrementAndGet()
                context.size.addAndGet(node.size)
                logger.info("Success to archive node[$node].")
            }
            if (shouldDeleteStorage) {
                // 删除原存储
                storageService.delete(sha256, getStorageCredentials(storageCredentialsKey)!!)
                logger.info("success delete file[$sha256]] in storage[$storageCredentialsKey]")
            }
            val archiveFileRequest = ArchiveFileRequest(
                sha256 = sha256,
                storageCredentialsKey = storageCredentialsKey,
                operator = SYSTEM_USER,
            )
            archiveClient.complete(archiveFileRequest)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): ArchivedNodeRestoreJob.ArchiveFile {
        return ArchivedNodeRestoreJob.ArchiveFile(
            id = row[ArchivedNodeRestoreJob.ArchiveFile::id.name].toString(),
            storageCredentialsKey = row[ArchivedNodeRestoreJob.ArchiveFile::storageCredentialsKey.name]?.toString(),
            lastModifiedBy = row[ArchivedNodeRestoreJob.ArchiveFile::storageCredentialsKey.name].toString(),
            sha256 = row[ArchivedNodeRestoreJob.ArchiveFile::sha256.name].toString(),
            size = row[ArchivedNodeRestoreJob.ArchiveFile::size.name].toString().toLong(),
        )
    }

    override fun entityClass(): KClass<ArchivedNodeRestoreJob.ArchiveFile> {
        return ArchivedNodeRestoreJob.ArchiveFile::class
    }

    private fun getStorageCredentials(key: String?): StorageCredentials? {
        return if (key == null) {
            storageProperties.defaultStorageCredentials()
        } else {
            RepositoryCommonUtils.getStorageCredentials(key)
        }
    }

    private fun archiveNode(projectId: String, repoName: String, fullPath: String) {
        val nodeArchiveRequest = NodeArchiveRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            operator = SYSTEM_USER,
        )
        nodeService.archiveNode(nodeArchiveRequest)
    }

    private fun listNode(sha256: String, storageCredentialsKey: String?): List<NodeCommonUtils.Node> {
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("archived").ne(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey, false)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchivedNodeCompleteJob::class.java)
    }
}
