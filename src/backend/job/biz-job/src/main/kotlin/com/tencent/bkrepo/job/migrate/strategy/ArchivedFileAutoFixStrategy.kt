/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.archive.ArchiveStatus.ARCHIVE_FAILED
import com.tencent.bkrepo.archive.ArchiveStatus.COMPLETED
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class ArchivedFileAutoFixStrategy(
    private val archiveFileDao: ArchiveFileDao,
    private val nodeDao: NodeDao,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
) : BaseAutoFixStrategy() {
    override fun fix(failedNode: TMigrateFailedNode): Boolean {
        with(failedNode) {
            val node = nodeDao.findById(projectId, nodeId) ?: return false
            val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
            val srcStorage = getStorageCredentials(storageProperties, repo.oldCredentialsKey)
            val dstStorage = getStorageCredentials(storageProperties, repo.storageCredentials?.key)
            val srcArchiveFile = archiveFileDao.findByStorageKeyAndSha256(repo.oldCredentialsKey, sha256)
            val dstArchiveFile = archiveFileDao.findByStorageKeyAndSha256(repo.storageCredentials?.key, sha256)

            if (node.archived != true) {
                return fixNodeNotArchived(node, srcStorage, dstStorage, srcArchiveFile, dstArchiveFile) ||
                        fixArchiveFailed(node, srcStorage, dstStorage, srcArchiveFile, dstArchiveFile)
            }

            return checkArchiveCompleted(node, srcArchiveFile, dstArchiveFile)
        }
    }

    /**
     * node源存储与目标存储均无数据，且未被标记为归档，但是存在归档记录，则将node标记为已归档
     */
    private fun fixNodeNotArchived(
        node: TNode,
        srcStorageCredentials: StorageCredentials,
        dstStorageCredentials: StorageCredentials,
        srcArchiveFile: TArchiveFile?,
        dstArchiveFile: TArchiveFile?
    ): Boolean {
        if (node.archived == true) {
            return false
        }

        if (srcArchiveFile?.status != COMPLETED && dstArchiveFile?.status != COMPLETED) {
            return false
        }

        val srcStorageExists = storageService.exist(node.sha256!!, srcStorageCredentials)
        val dstStorageExists = storageService.exist(node.sha256!!, dstStorageCredentials)
        if (srcStorageExists || dstStorageExists) {
            return false
        }

        nodeDao.setNodeArchived(node.projectId, node.id!!, true)
        logger.info("set node ${node.id} archived[true]")
        return true
    }

    /**
     * 确认是否数据在缓存未上传到COS存储导致的归档失败，并移除归档失败状态的记录
     */
    private fun fixArchiveFailed(
        node: TNode,
        srcStorageCredentials: StorageCredentials,
        dstStorageCredentials: StorageCredentials,
        srcArchiveFile: TArchiveFile?,
        dstArchiveFile: TArchiveFile?
    ): Boolean {
        if (node.archived == true) {
            return false
        }

        if (srcArchiveFile?.status != ARCHIVE_FAILED && dstArchiveFile?.status != ARCHIVE_FAILED) {
            return false
        }

        val artifactInputStream = storageService.load(node.sha256!!, Range.FULL_RANGE, srcStorageCredentials)
            ?: storageService.load(node.sha256!!, Range.FULL_RANGE, dstStorageCredentials)
            ?: return false
        artifactInputStream.close()

        // 在源存储或目标存储中存在数据时可直接移除归档记录，移除之后将继续迁移数据
        if (srcArchiveFile?.status == ARCHIVE_FAILED) {
            archiveFileDao.removeById(srcArchiveFile.id!!)
            logger.info("remove failed src archive file[${srcArchiveFile.sha256}]")
        }

        if (dstArchiveFile?.status == ARCHIVE_FAILED) {
            archiveFileDao.removeById(dstArchiveFile.id!!)
            logger.info("remove failed dst archive file[${dstArchiveFile.sha256}]")
        }

        return true
    }

    private fun checkArchiveCompleted(
        node: TNode,
        srcArchiveFile: TArchiveFile?,
        dstArchiveFile: TArchiveFile?
    ): Boolean {
        val sha256 = node.sha256
        val projectId = node.projectId
        val repoName = node.repoName
        if (srcArchiveFile != null && srcArchiveFile.status != COMPLETED) {
            logger.info("node[$sha256] archive status[${srcArchiveFile.status}], task[$projectId/$repoName]")
            return false
        }

        // 目标存储已存在同sha256的归档文件，待其状态稳定后才可继续迁移
        if (dstArchiveFile != null && dstArchiveFile.status != COMPLETED) {
            logger.info("node[$sha256] dst archive status[${dstArchiveFile.status}], task[$projectId/$repoName]")
            return false
        }

        // 已经处于可迁移状态，继续尝试迁移
        return srcArchiveFile != null || dstArchiveFile != null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchivedFileAutoFixStrategy::class.java)
    }
}
