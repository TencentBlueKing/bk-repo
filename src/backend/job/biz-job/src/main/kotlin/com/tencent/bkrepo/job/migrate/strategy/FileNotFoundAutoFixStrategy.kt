/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.strategy

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils.shardingSequenceFor
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.task.archive.ArchivedNodeRestoreJob
import com.tencent.bkrepo.job.batch.task.archive.NodeCompressedJob
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.dao.ArchiveMigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TArchiveMigrateFailedNode
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class FileNotFoundAutoFixStrategy(
    private val mongoTemplate: MongoTemplate,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val archiveMigrateFailedNodeDao: ArchiveMigrateFailedNodeDao,
) : MigrateFailedNodeAutoFixStrategy {
    override fun fix(failedNode: TMigrateFailedNode): Boolean {
        val projectId = failedNode.projectId
        val repoName = failedNode.repoName
        val fullPath = failedNode.fullPath
        val sha256 = failedNode.sha256
        val oldCredentials = getOldCredentials(projectId, repoName)
        if (storageService.exist(sha256, oldCredentials)) {
            // 只处理源文件不存在的情况，文件存在时直接返回
            return false
        }
        val node = findNode(projectId, repoName, fullPath)

        // 检查是否被归档或压缩
        if (archivedOrCompressed(node)) {
            logger.info("node[$fullPath] was archived or compressed, task[$projectId/$repoName]")
            return false
        }

        // 尝试从其他存储复制过来
        if (copyFromOtherStorage(failedNode, oldCredentials)) {
            return true
        }

        // 所有存储都找不到时表示源文件丢失，归档failedNode以使迁移任务继续执行
        logger.error("node[$fullPath], sha256[$sha256] lost!, archive migrate failed node, task[$projectId/$repoName]")
        archiveMigrateFailedNodeDao.insert(TArchiveMigrateFailedNode.convert(failedNode))
        migrateFailedNodeDao.remove(projectId, repoName, fullPath)
        return true
    }

    private fun archivedOrCompressed(node: Node): Boolean {
        // node已经被归档或压缩，需要先恢复到原存储再迁移
        if (node.archived == true || node.compressed == true) {
            return true
        }

        // 查看是否存在归档任务
        val archivedFile = mongoTemplate.findOne(
            Query.query(Criteria.where("sha256").isEqualTo(node.sha256)),
            ArchivedNodeRestoreJob.ArchiveFile::class.java, "archive_file"
        )
        if (archivedFile != null) {
            logger.info("node[${node.fullPath}] exists archived file, task[${node.projectId}/${node.repoName}]")
            return true
        }

        // 查看是否存在压缩任务
        val compressedFile = mongoTemplate.findOne(
            Query.query(Criteria.where("sha256").isEqualTo(node.sha256)),
            NodeCompressedJob.CompressFile::class.java, "compress_file"
        )
        if (compressedFile != null) {
            logger.info("node[${node.fullPath}] exists compressed file, task[${node.projectId}/${node.repoName}]")
            return true
        }
        return false
    }

    private fun copyFromOtherStorage(
        migrateFailedNode: TMigrateFailedNode,
        oldCredentials: StorageCredentials
    ): Boolean {
        val projectId = migrateFailedNode.projectId
        val repoName = migrateFailedNode.repoName
        val fullPath = migrateFailedNode.fullPath

        val allCredentials = storageCredentialsClient.list().data!! + storageProperties.defaultStorageCredentials()
        allCredentials.forEach { credentials ->
            val key = credentials.key
            try {
                // 可能文件还存在于缓存中，因此使用load而不是exists判断文件是否存在
                val ais = storageService.load(migrateFailedNode.sha256, Range.full(migrateFailedNode.size), credentials)
                if (ais != null) {
                    ais.close()
                    // 尝试从其他存储复制到当前存储
                    storageService.copy(migrateFailedNode.sha256, credentials, oldCredentials)
                    logger.info("copy [$fullPath] from credentials[$key] success, task[$projectId/$repoName]")
                    return true
                }
            } catch (e: Exception) {
                logger.error("check node[$fullPath] in $key failed, task[$projectId/ $repoName]", e)
            }
        }
        return false
    }

    private fun findNode(projectId: String, repoName: String, fullPath: String): Node {
        val collectionName = "node_${shardingSequenceFor(projectId, SHARDING_COUNT)}"
        val criteria = Criteria
            .where(Node::projectId.name).isEqualTo(projectId)
            .and(Node::repoName.name).isEqualTo(repoName)
            .and(Node::fullPath.name).isEqualTo(fullPath)
        return mongoTemplate.findOne(Query(criteria), Node::class.java, collectionName)!!
    }

    private fun getOldCredentials(projectId: String, repoName: String): StorageCredentials {
        val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
        val oldCredentialsKey = repo.oldCredentialsKey
        return if (oldCredentialsKey == null) {
            storageProperties.defaultStorageCredentials()
        } else {
            RepositoryCommonUtils.getStorageCredentials(oldCredentialsKey)!!
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileNotFoundAutoFixStrategy::class.java)
    }
}
