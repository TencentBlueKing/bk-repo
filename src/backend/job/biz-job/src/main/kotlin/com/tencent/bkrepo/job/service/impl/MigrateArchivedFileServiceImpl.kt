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

package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.ArchiveStatus.COMPLETED
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.job.service.MigrateArchivedFileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MigrateArchivedFileServiceImpl(
    private val archiveFileDao: ArchiveFileDao,
) : MigrateArchivedFileService {
    override fun migrateArchivedFile(context: MigrationContext, node: Node): Boolean {
        return migrateArchivedFile(context.task.srcStorageKey, context.task.dstStorageKey, node.sha256)
    }

    override fun migrateArchivedFile(srcStorageKey: String?, dstStorageKey: String?, sha256: String): Boolean {
        // 检查源存储归档文件及其状态是否可迁移
        val srcArchiveFile = archiveFileDao.findByStorageKeyAndSha256(srcStorageKey, sha256)
        if (srcArchiveFile != null && srcArchiveFile.status != COMPLETED) {
            // 制品已经处于归档完成的状态才可迁移，其他状态迁移会导致迁移后的归档文件无法完成归档，或者重复恢复
            throw IllegalStateException("migrate archived file[$sha256] failed, status[${srcArchiveFile.status}]")
        }

        // 检查目标存储是否已经存在sha256对应的归档文件及其状态是否可直接被复用
        val dstArchivedFile = archiveFileDao.findByStorageKeyAndSha256(dstStorageKey, sha256)
        if (dstArchivedFile?.status == COMPLETED) {
            return true
        }
        if (dstArchivedFile != null) {
            // 状态不为COMPLETED时禁止迁移
            throw IllegalStateException(
                "migrate archived file[$sha256] failed, dst archive file status[${dstArchivedFile.status}]"
            )
        }

        // 源存储与目标存储都不存在归档文件时不迁移
        if (srcArchiveFile == null) {
            return false
        }

        // 迁移归档文件
        val migratedArchiveFile = TArchiveFile(
            createdBy = srcArchiveFile.createdBy,
            createdDate = srcArchiveFile.createdDate,
            lastModifiedBy = srcArchiveFile.lastModifiedBy,
            lastModifiedDate = srcArchiveFile.lastModifiedDate,
            sha256 = srcArchiveFile.sha256,
            size = srcArchiveFile.size,
            compressedSize = srcArchiveFile.compressedSize,
            storageCredentialsKey = dstStorageKey,
            status = COMPLETED,
            archiver = srcArchiveFile.archiver,
            archiveCredentialsKey = srcArchiveFile.archiveCredentialsKey,
            storageClass = srcArchiveFile.storageClass
        )
        archiveFileDao.insert(migratedArchiveFile)
        logger.info("migrate archived file[$sha256] of storage[$srcStorageKey] success")
        return true
    }

    override fun archivedFileCompleted(storageKey: String?, sha256: String): Boolean {
        val archiveFile = archiveFileDao.findByStorageKeyAndSha256(storageKey, sha256) ?: return false
        if (archiveFile.status != COMPLETED) {
            throw IllegalStateException("archived file[$sha256] not completed, status[${archiveFile.status}]")
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateArchivedFileServiceImpl::class.java)
    }
}
