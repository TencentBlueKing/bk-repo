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

import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.pypi.util.PypiSimpleIndexUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("PyPI simple 单包索引文件缓存")
class PypiSimpleIndexCacheServiceTest {

    private val nodeService: NodeService = mockk(relaxed = true)
    private val storageManager: StorageManager = mockk(relaxed = true)
    private val lockOperation: LockOperation = mockk(relaxed = true)
    private lateinit var service: PypiSimpleIndexCacheService

    @BeforeEach
    fun setUp() {
        clearMocks(nodeService, storageManager, lockOperation)
        service = PypiSimpleIndexCacheService(nodeService, storageManager, lockOperation)
    }

    @Test
    @DisplayName("命中缓存时返回 HTML，不写存储")
    fun loadReturnsCachedHtml() {
        val html = "<html>cached</html>"
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("My_Package")
        val node = nodeDetail(fullPath)
        every { nodeService.getNodeDetail(any(), any()) } returns node
        every {
            storageManager.loadFullArtifactInputStream(node, null)
        } returns ArtifactInputStream(html.byteInputStream(), Range.full(html.length.toLong()))

        val result = service.load(PROJECT, REPO, "My_Package", null)

        assertEquals(html, result)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("未命中缓存返回 null")
    fun loadReturnsNullWhenMissing() {
        every { nodeService.getNodeDetail(any(), any()) } returns null

        assertNull(service.load(PROJECT, REPO, "demo", null))
        verify(exactly = 0) { storageManager.loadFullArtifactInputStream(any(), any()) }
    }

    @Test
    @DisplayName("获锁后写入规范化路径缓存")
    fun tryStoreWritesWhenLockAcquired() {
        val lock = Any()
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("My.Package")
        every { lockOperation.getLock(any()) } returns lock
        every { lockOperation.acquireLock(any(), lock) } returns true
        every { lockOperation.close(any(), lock) } returns Unit
        every { storageManager.storeArtifactFile(any(), any(), any()) } returns nodeDetail(fullPath)

        val stored = service.tryStore(PROJECT, REPO, "My.Package", "<html>new</html>", "user", null)

        assertTrue(stored)
        verify {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> {
                    it.fullPath == fullPath && it.overwrite && !it.folder
                },
                any(),
                null
            )
        }
        verify { lockOperation.close(any(), lock) }
    }

    @Test
    @DisplayName("未获锁时不写缓存")
    fun tryStoreSkipsWriteWhenLockNotAcquired() {
        val lock = Any()
        every { lockOperation.getLock(any()) } returns lock
        every { lockOperation.acquireLock(any(), lock) } returns false

        val stored = service.tryStore(PROJECT, REPO, "demo", "<html>x</html>", "user", null)

        assertFalse(stored)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
        verify(exactly = 0) { lockOperation.close(any(), any()) }
    }

    @Test
    @DisplayName("锁异常时不写缓存")
    fun tryStoreSkipsWriteWhenLockFails() {
        every { lockOperation.getLock(any()) } throws RuntimeException("redis down")

        val stored = service.tryStore(PROJECT, REPO, "demo", "<html>x</html>", "user", null)

        assertFalse(stored)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("invalidate 删除规范化路径缓存")
    fun invalidateDeletesNormalizedPath() {
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("My_Package")
        every { nodeService.deleteNode(any()) } returns mockk(relaxed = true)

        service.invalidate(PROJECT, REPO, "My_Package")

        verify {
            nodeService.deleteNode(
                match<NodeDeleteRequest> { it.fullPath == fullPath && it.projectId == PROJECT && it.repoName == REPO }
            )
        }
    }

    @Test
    @DisplayName("invalidate 失败不抛异常")
    fun invalidateSwallowsErrors() {
        every { nodeService.deleteNode(any()) } throws RuntimeException("storage error")

        service.invalidate(PROJECT, REPO, "demo")
    }

    @Test
    @DisplayName("PEP503 规范化包名")
    fun normalizePackageName() {
        assertEquals("my-package", PypiSimpleIndexUtils.normalizePackageName("My_Package"))
        assertEquals("my-package", PypiSimpleIndexUtils.normalizePackageName("my.package"))
        assertEquals(
            "/.pypi-simple-index/packages/my-package.html",
            PypiSimpleIndexUtils.packageCacheFullPath("My_Package")
        )
        assertTrue(PypiSimpleIndexUtils.isSimpleIndexCacheFolder(".pypi-simple-index"))
        assertFalse(PypiSimpleIndexUtils.isSimpleIndexCacheFolder("requests"))
    }

    private fun nodeDetail(fullPath: String): NodeDetail {
        val name = fullPath.substringAfterLast('/')
        val path = fullPath.substringBeforeLast('/') + "/"
        val info = NodeInfo(
            createdBy = "system",
            createdDate = LocalDateTime.now().toString(),
            lastModifiedBy = "system",
            lastModifiedDate = LocalDateTime.now().toString(),
            folder = false,
            path = path,
            name = name,
            fullPath = fullPath,
            size = 10,
            projectId = PROJECT,
            repoName = REPO,
            sha256 = "a".repeat(64),
        )
        return NodeDetail(info)
    }

    companion object {
        private const val PROJECT = "p-test"
        private const val REPO = "pypi-local"
    }
}
