/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
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

package com.tencent.bkrepo.pypi.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.pypi.artifact.PypiProperties
import com.tencent.bkrepo.pypi.util.PypiSimpleIndexUtils
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime

/**
 * LOCAL PyPI `/simple/{package}/` HTML 文件缓存（仓库内节点）。
 * HTML 生成仍由 [com.tencent.bkrepo.pypi.artifact.repository.PypiLocalRepository] 负责。
 */
@Service
class PypiSimpleIndexCacheService(
    private val nodeService: NodeService,
    private val storageManager: StorageManager,
    private val lockOperation: LockOperation,
    private val pypiProperties: PypiProperties,
) {

    /**
     * 读取单包 simple HTML 缓存；不存在或已过期返回 null（不缓存 miss）。
     */
    fun load(
        projectId: String,
        repoName: String,
        packageName: String,
        storageCredentials: StorageCredentials?,
    ): String? {
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath(packageName)
        val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath)) ?: return null
        if (node.folder) {
            return null
        }
        if (isExpired(node)) {
            logger.info(
                "Pypi simple index cache expired[$projectId/$repoName$fullPath], " +
                    "lastModifiedDate[${node.lastModifiedDate}], ttl[${pypiProperties.simpleIndexCacheTtl}]"
            )
            return null
        }
        return storageManager.loadFullArtifactInputStream(node, storageCredentials)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        }
    }

    /**
     * 尝试写入缓存。抢到锁才写并返回 true；未抢到或锁异常时不写并返回 false。
     */
    fun tryStore(
        projectId: String,
        repoName: String,
        packageName: String,
        html: String,
        userId: String,
        storageCredentials: StorageCredentials?,
    ): Boolean {
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath(packageName)
        val lockKey = lockKey(projectId, repoName, fullPath)
        val lock = try {
            lockOperation.getLock(lockKey)
        } catch (e: Exception) {
            logger.error("Failed to get lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return false
        }
        val locked = try {
            lockOperation.acquireLock(lockKey, lock)
        } catch (e: Exception) {
            logger.error("Failed to acquire lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return false
        }
        if (!locked) {
            return false
        }
        try {
            storeHtml(projectId, repoName, fullPath, html, userId, storageCredentials)
            return true
        } catch (e: Exception) {
            logger.error("Failed to store pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return false
        } finally {
            try {
                lockOperation.close(lockKey, lock)
            } catch (e: Exception) {
                logger.error("Failed to release lock for pypi simple index cache[$lockKey]", e)
            }
        }
    }

    /**
     * 删除单包缓存；失败只记日志，不抛异常。
     */
    fun invalidate(projectId: String, repoName: String, packageName: String) {
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath(packageName)
        try {
            nodeService.deleteNode(
                NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = SYSTEM_USER,
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to invalidate pypi simple index cache[$projectId/$repoName$fullPath]", e)
        }
    }

    private fun storeHtml(
        projectId: String,
        repoName: String,
        fullPath: String,
        html: String,
        userId: String,
        storageCredentials: StorageCredentials?,
    ) {
        val tempFile = File.createTempFile("pypi-simple-index-", ".html")
        try {
            tempFile.writeText(html, StandardCharsets.UTF_8)
            val artifactFile = FileSystemArtifactFile(tempFile)
            val request = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                folder = false,
                overwrite = true,
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5(),
                crc64ecma = artifactFile.getFileCrc64ecma(),
                operator = userId.ifBlank { SYSTEM_USER },
            )
            storageManager.storeArtifactFile(request, artifactFile, storageCredentials)
            artifactFile.delete()
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun isExpired(node: NodeDetail): Boolean {
        val ttl = pypiProperties.simpleIndexCacheTtl
        if (ttl.isZero || ttl.isNegative) {
            return false
        }
        return try {
            val lastModified = LocalDateTime.parse(node.lastModifiedDate)
            Duration.between(lastModified, LocalDateTime.now()) >= ttl
        } catch (e: Exception) {
            logger.warn(
                "Failed to parse cache lastModifiedDate[${node.lastModifiedDate}] " +
                    "for[${node.projectId}/${node.repoName}${node.fullPath}], treat as expired",
                e
            )
            true
        }
    }

    private fun lockKey(projectId: String, repoName: String, fullPath: String): String {
        return "$LOCK_KEY_PREFIX$projectId/$repoName$fullPath"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PypiSimpleIndexCacheService::class.java)
        private const val LOCK_KEY_PREFIX = "pypi:simple:lock:"
    }
}
