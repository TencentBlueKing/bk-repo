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

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.pypi.artifact.PypiProperties
import com.tencent.bkrepo.pypi.exception.PypiSimpleNotFoundException
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
 * LOCAL PyPI `/simple/` 与 `/simple/{package}/` HTML 文件缓存（仓库内节点，写入存储热缓存）。
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
     * 读取或生成索引 HTML。
     * 索引文件缺失只表示从未生成或制品已删光，必须 [compute] 回源，缓存层不映射为 404。
     * 文件未过期时直接读存储。过期用 Redis tryLock 单飞覆盖，抢不到立刻返回旧文件。
     * 节点存在但读不到内容：回源重建并返回 HTML，不编 503。
     * 节点不存在时 tryLock：抢到则生成；抢不到再读一次，有文件就返回，没有则 503 让客户端重试。
     * 禁止在请求线程上自旋等锁。[compute] 返回 null 时原样返回 null，不写文件。
     */
    fun getOrCompute(
        projectId: String,
        repoName: String,
        packageName: String?,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ): String? {
        val fullPath = PypiSimpleIndexUtils.cacheFullPath(packageName)
        val node = loadNode(projectId, repoName, fullPath)
        if (node != null) {
            val html = readHtml(node, storageCredentials)
            if (html != null) {
                if (!isExpired(node)) {
                    return html
                }
                return refreshExpired(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    userId = userId,
                    storageCredentials = storageCredentials,
                    staleHtml = html,
                    compute = compute,
                )
            }
            return restoreUnreadable(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                userId = userId,
                storageCredentials = storageCredentials,
                compute = compute,
            )
        }
        return computeOnMiss(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            userId = userId,
            storageCredentials = storageCredentials,
            compute = compute,
        )
    }

    /**
     * 上传/删除后覆盖重建单包索引，不删文件，避免 miss 窗口被流量打满。
     * 不重建根目录 `/simple/`：pip install 只打 `/simple/{name}/`；根列表靠 TTL SWR。
     * 仅当 [computePackage] 确认制品不存在时才删除对应文件。失败只记日志，不抛异常。
     */
    fun invalidate(
        projectId: String,
        repoName: String,
        packageName: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        computePackage: () -> String?,
    ) {
        refreshUnderLock(
            projectId = projectId,
            repoName = repoName,
            fullPath = PypiSimpleIndexUtils.packageCacheFullPath(packageName),
            userId = userId,
            storageCredentials = storageCredentials,
            compute = computePackage,
        )
    }

    private fun refreshExpired(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        staleHtml: String,
        compute: () -> String?,
    ): String? {
        val redisKey = lockKey(projectId, repoName, fullPath)
        val lock = try {
            lockOperation.getLock(redisKey)
        } catch (e: Exception) {
            logger.error("Failed to get lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return staleHtml
        }
        val locked = try {
            lockOperation.acquireLock(redisKey, lock)
        } catch (e: Exception) {
            logger.error("Failed to acquire lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return staleHtml
        }
        if (!locked) {
            return staleHtml
        }
        try {
            val latest = loadEntry(projectId, repoName, fullPath, storageCredentials)
            if (latest != null && !isExpired(latest.node)) {
                return latest.html
            }
            if (latest == null) {
                return storeComputed(
                    projectId, repoName, fullPath, userId, storageCredentials, compute,
                )
            }
            return try {
                storeComputed(
                    projectId, repoName, fullPath, userId, storageCredentials, compute,
                ) ?: latest.html
            } catch (e: Exception) {
                logger.error(
                    "Failed to refresh pypi simple index cache[$projectId/$repoName$fullPath], keep stale file",
                    e
                )
                latest.html
            }
        } finally {
            closeQuietly(redisKey, lock)
        }
    }

    private fun restoreUnreadable(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ): String? {
        val redisKey = lockKey(projectId, repoName, fullPath)
        val lock = try {
            lockOperation.getLock(redisKey)
        } catch (e: Exception) {
            logger.error("Failed to get lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return readOrRestore(projectId, repoName, fullPath, userId, storageCredentials, compute)
        }
        val locked = try {
            lockOperation.acquireLock(redisKey, lock)
        } catch (e: Exception) {
            logger.error("Failed to acquire lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return readOrRestore(projectId, repoName, fullPath, userId, storageCredentials, compute)
        }
        if (!locked) {
            return readOrRestore(projectId, repoName, fullPath, userId, storageCredentials, compute)
        }
        try {
            return readOrRestore(projectId, repoName, fullPath, userId, storageCredentials, compute)
        } finally {
            closeQuietly(redisKey, lock)
        }
    }

    private fun computeOnMiss(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ): String? {
        val redisKey = lockKey(projectId, repoName, fullPath)
        val lock = try {
            lockOperation.getLock(redisKey)
        } catch (e: Exception) {
            logger.error("Failed to get lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return loadOrRetryLater(
                projectId, repoName, fullPath, userId, storageCredentials, compute,
            )
        }
        val locked = try {
            lockOperation.acquireLock(redisKey, lock)
        } catch (e: Exception) {
            logger.error("Failed to acquire lock for pypi simple index cache[$projectId/$repoName$fullPath]", e)
            return loadOrRetryLater(
                projectId, repoName, fullPath, userId, storageCredentials, compute,
            )
        }
        if (!locked) {
            return loadOrRetryLater(
                projectId, repoName, fullPath, userId, storageCredentials, compute,
            )
        }
        try {
            val raced = loadEntry(projectId, repoName, fullPath, storageCredentials)
            if (raced != null) {
                return raced.html
            }
            return storeComputed(projectId, repoName, fullPath, userId, storageCredentials, compute)
        } finally {
            closeQuietly(redisKey, lock)
        }
    }

    private fun readOrRestore(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ): String? {
        val node = loadNode(projectId, repoName, fullPath)
        if (node != null) {
            readHtml(node, storageCredentials)?.let { return it }
        }
        return storeComputed(projectId, repoName, fullPath, userId, storageCredentials, compute)
    }

    private fun storeComputed(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ): String? {
        val html = compute() ?: return null
        try {
            storeHtml(projectId, repoName, fullPath, html, userId, storageCredentials)
        } catch (e: Exception) {
            logger.error("Failed to store pypi simple index cache[$projectId/$repoName$fullPath]", e)
        }
        return html
    }

    private fun refreshUnderLock(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ) {
        val redisKey = lockKey(projectId, repoName, fullPath)
        var lock: Any? = null
        var locked = false
        try {
            lock = lockOperation.getLock(redisKey)
            locked = lockOperation.getSpinLock(redisKey, lock)
            overwriteOrRemove(
                projectId, repoName, fullPath, userId, storageCredentials, compute,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to lock while refreshing pypi simple index[$projectId/$repoName$fullPath]",
                e
            )
            overwriteOrRemove(
                projectId, repoName, fullPath, userId, storageCredentials, compute,
            )
        } finally {
            if (locked && lock != null) {
                closeQuietly(redisKey, lock)
            }
        }
    }

    private fun overwriteOrRemove(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ) {
        try {
            storeComputed(projectId, repoName, fullPath, userId, storageCredentials, compute)
        } catch (e: PypiSimpleNotFoundException) {
            deleteQuietly(projectId, repoName, fullPath)
        } catch (e: Exception) {
            logger.error(
                "Failed to refresh pypi simple index cache[$projectId/$repoName$fullPath], keep existing file",
                e
            )
        }
    }

    private fun loadOrRetryLater(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        storageCredentials: StorageCredentials?,
        compute: () -> String?,
    ): String? {
        val node = loadNode(projectId, repoName, fullPath)
        if (node != null) {
            readHtml(node, storageCredentials)?.let { return it }
            return storeComputed(projectId, repoName, fullPath, userId, storageCredentials, compute)
        }
        throw unavailable()
    }

    private fun loadEntry(
        projectId: String,
        repoName: String,
        fullPath: String,
        storageCredentials: StorageCredentials?,
    ): CacheEntry? {
        val node = loadNode(projectId, repoName, fullPath) ?: return null
        val html = readHtml(node, storageCredentials) ?: return null
        return CacheEntry(node, html)
    }

    private fun loadNode(projectId: String, repoName: String, fullPath: String): NodeDetail? {
        val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath)) ?: return null
        return node.takeUnless { it.folder }
    }

    private fun readHtml(node: NodeDetail, storageCredentials: StorageCredentials?): String? {
        return storageManager.loadFullArtifactInputStream(node, storageCredentials)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        }
    }

    private fun unavailable(): Nothing {
        throw ErrorCodeException(
            messageCode = CommonMessageCode.SYSTEM_ERROR,
            status = HttpStatus.SERVICE_UNAVAILABLE,
        )
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

    private fun deleteQuietly(projectId: String, repoName: String, fullPath: String) {
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

    private fun closeQuietly(lockKey: String, lock: Any) {
        try {
            lockOperation.close(lockKey, lock)
        } catch (e: Exception) {
            logger.error("Failed to release lock for pypi simple index cache[$lockKey]", e)
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

    private data class CacheEntry(
        val node: NodeDetail,
        val html: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(PypiSimpleIndexCacheService::class.java)
        private const val LOCK_KEY_PREFIX = "pypi:simple:lock:"
    }
}
