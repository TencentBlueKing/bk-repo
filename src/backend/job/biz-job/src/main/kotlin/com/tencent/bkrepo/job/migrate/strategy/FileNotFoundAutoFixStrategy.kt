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

import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.dao.ArchiveMigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TArchiveMigrateFailedNode
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FileNotFoundAutoFixStrategy(
    private val nodeDao: NodeDao,
    private val archiveFileDao: ArchiveFileDao,
    private val compressFileDao: CompressFileDao,
    private val storageCredentialService: StorageCredentialService,
    private val fileReferenceService: FileReferenceService,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val archiveMigrateFailedNodeDao: ArchiveMigrateFailedNodeDao,
) : BaseAutoFixStrategy() {
    override fun fix(failedNode: TMigrateFailedNode): Boolean {
        val projectId = failedNode.projectId
        val repoName = failedNode.repoName
        val fullPath = failedNode.fullPath
        val sha256 = failedNode.sha256
        val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
        val oldCredentials = getStorageCredentials(storageProperties, repo.oldCredentialsKey)
        if (sha256 == FAKE_SHA256) {
            return false
        }
        if (storageService.exist(sha256, oldCredentials)) {
            // 文件存在
            return true
        }
        val node = nodeDao.findById(projectId, failedNode.nodeId) ?: return false

        // 检查是否被归档或压缩
        if (archivedOrCompressed(node, repo.oldCredentialsKey, repo.storageCredentials?.key)) {
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

    private fun archivedOrCompressed(node: TNode, srcStorageKey: String?, dstStorageKey: String?): Boolean {
        // node已经被归档或压缩，需要先恢复到原存储再迁移
        if (node.archived == true || node.compressed == true) {
            return true
        }

        // 查看是否存在归档任务
        val archivedFile = archiveFileDao.findByStorageKeyAndSha256(srcStorageKey, node.sha256!!)
            ?: archiveFileDao.findByStorageKeyAndSha256(dstStorageKey, node.sha256!!)
        if (archivedFile != null) {
            logger.info("node[${node.fullPath}] exists archived file, task[${node.projectId}/${node.repoName}]")
            return true
        }

        // 查看是否存在压缩任务
        val compressedFile = compressFileDao.findByStorageKeyAndSha256(srcStorageKey, node.sha256!!)
            ?: compressFileDao.findByStorageKeyAndSha256(dstStorageKey, node.sha256!!)
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

        val allCredentials = storageCredentialService.list() + storageProperties.defaultStorageCredentials()
        allCredentials.forEach { credentials ->
            val key = credentials.key
            try {
                // 可能文件还存在于缓存中，因此使用load而不是exists判断文件是否存在
                val ais = storageService.load(migrateFailedNode.sha256, Range.full(migrateFailedNode.size), credentials)
                if (ais != null) {
                    ais.close()
                    // 尝试从其他存储复制到当前存储
                    storageService.copy(migrateFailedNode.sha256, credentials, oldCredentials)
                    fileReferenceService.increment(migrateFailedNode.sha256, oldCredentials.key, 0L)
                    logger.info("copy [$fullPath] from credentials[$key] success, task[$projectId/$repoName]")
                    return true
                }
            } catch (e: Exception) {
                logger.error("copy node[$fullPath] from $key failed, task[$projectId/ $repoName]", e)
            }
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileNotFoundAutoFixStrategy::class.java)
    }
}
