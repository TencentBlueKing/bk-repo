/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class ArchivedFileAutoFixStrategy(
    private val archiveFileDao: ArchiveFileDao
) : MigrateFailedNodeAutoFixStrategy {
    override fun fix(failedNode: TMigrateFailedNode): Boolean {
        with(failedNode) {
            val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
            val srcArchiveFile = archiveFileDao.findByStorageKeyAndSha256(repo.oldCredentialsKey, sha256)
            if (srcArchiveFile != null && srcArchiveFile.status != ArchiveStatus.COMPLETED) {
                logger.info("node[$sha256] archive status[${srcArchiveFile.status}], task[$projectId/$repoName]")
                return false
            }

            // 目标存储已存在同sha256的归档文件，待其状态稳定后才可继续迁移
            val dstArchiveFile = archiveFileDao.findByStorageKeyAndSha256(repo.storageCredentials?.key, sha256)
            if (dstArchiveFile != null && dstArchiveFile.status != ArchiveStatus.COMPLETED) {
                logger.info("node[$sha256] dst archive status[${dstArchiveFile.status}], task[$projectId/$repoName]")
                return false
            }

            // 已经处于可迁移状态，继续尝试迁移
            return srcArchiveFile != null || dstArchiveFile != null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchivedFileAutoFixStrategy::class.java)
    }
}
