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
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.pypi.artifact.PypiProperties
import com.tencent.bkrepo.pypi.exception.PypiSimpleNotFoundException
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("PyPI simple 索引文件缓存")
class PypiSimpleIndexCacheServiceTest {

    private val nodeService: NodeService = mockk(relaxed = true)
    private val storageManager: StorageManager = mockk(relaxed = true)
    private val lockOperation: LockOperation = mockk(relaxed = true)
    private val pypiProperties = PypiProperties().apply {
        enableSimpleIndexCache = true
        simpleIndexCacheTtl = Duration.ofMinutes(1)
    }
    private lateinit var service: PypiSimpleIndexCacheService

    @BeforeEach
    fun setUp() {
        clearMocks(nodeService, storageManager, lockOperation)
        pypiProperties.enableSimpleIndexCache = true
        pypiProperties.simpleIndexCacheTtl = Duration.ofMinutes(1)
        stubLocks()
        every { storageManager.storeArtifactFile(any(), any(), any()) } answers {
            val request = invocation.args[0] as NodeCreateRequest
            nodeDetail(request.fullPath)
        }
        every { nodeService.deleteNode(any()) } returns mockk(relaxed = true)
        service = PypiSimpleIndexCacheService(nodeService, storageManager, lockOperation, pypiProperties)
    }

    @Test
    @DisplayName("未过期时读已有文件，不重建")
    fun hitReturnsCachedHtmlWithoutCompute() {
        val html = "<html>cached</html>"
        stubCachedFile(PypiSimpleIndexUtils.packageCacheFullPath("My_Package"), html)

        val result = getHtml("My_Package") { error("compute") }

        assertEquals(html, result)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
        verify(exactly = 0) { lockOperation.acquireLock(any(), any()) }
    }

    @Test
    @DisplayName("TTL 到期未抢到锁时仍返回旧文件，不扫库")
    fun expiredWithoutLockReturnsStaleHtml() {
        val html = "<html>stale</html>"
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("demo")
        stubCachedFile(fullPath, html, lastModifiedDate = LocalDateTime.now().minusMinutes(2))
        stubLocks(tryLock = false)

        var computes = 0
        val result = getHtml("demo") {
            computes++
            "<html>new</html>"
        }

        assertEquals(html, result)
        assertEquals(0, computes)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("TTL 到期抢到锁时覆盖重建并返回新 HTML")
    fun expiredWithLockRebuildsOnce() {
        val stale = "<html>stale</html>"
        val fresh = "<html>fresh</html>"
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("demo")
        stubCachedFile(fullPath, stale, lastModifiedDate = LocalDateTime.now().minusMinutes(2))
        stubLocks(tryLock = true)

        var computes = 0
        val result = getHtml("demo") {
            computes++
            fresh
        }

        assertEquals(fresh, result)
        assertEquals(1, computes)
        verify {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> { it.fullPath == fullPath && it.overwrite },
                any(),
                null
            )
        }
    }

    @Test
    @DisplayName("TTL 小于等于 0 时不过期、不重建")
    fun ttlDisabledNeverExpires() {
        pypiProperties.simpleIndexCacheTtl = Duration.ZERO
        val html = "<html>cached</html>"
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("demo")
        stubCachedFile(fullPath, html, lastModifiedDate = LocalDateTime.now().minusDays(30))

        var computes = 0
        assertEquals(html, getHtml("demo") {
            computes++
            "<html>new</html>"
        })
        assertEquals(0, computes)
    }

    @Test
    @DisplayName("miss 时锁内生成并写入热缓存文件")
    fun missComputesUnderLockAndStores() {
        every { nodeService.getNodeDetail(any(), any()) } returns null
        stubLocks(spin = true)
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("My.Package")

        var computes = 0
        val result = getHtml("My.Package") {
            computes++
            "<html>new</html>"
        }

        assertEquals("<html>new</html>", result)
        assertEquals(1, computes)
        verify {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> {
                    it.fullPath == fullPath && it.overwrite && !it.folder
                },
                any(),
                null
            )
        }
        verify { lockOperation.acquireLock(any(), any()) }
        verify(exactly = 0) { lockOperation.getSpinLock(any(), any()) }
        verify(exactly = 0) { lockOperation.getSpinLock(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("miss 未抢到锁时若文件已写出则直接返回，不扫库")
    fun missWithoutLockReturnsFileIfPresent() {
        val html = "<html>new</html>"
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("demo")
        val node = nodeDetail(fullPath)
        every { nodeService.getNodeDetail(any(), any()) } returnsMany listOf(null, node)
        every { storageManager.loadFullArtifactInputStream(any(), null) } answers { htmlStream(html) }
        stubLocks(tryLock = false)

        var computes = 0
        val result = getHtml("demo") {
            computes++
            "<html>other</html>"
        }

        assertEquals(html, result)
        assertEquals(0, computes)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("miss 未抢到锁且无文件时 503，不编 429、不扫库")
    fun missWithoutLockAndNoFileFailsFast() {
        every { nodeService.getNodeDetail(any(), any()) } returns null
        stubLocks(tryLock = false)
        var computes = 0

        val error = assertThrows<ErrorCodeException> {
            getHtml("demo") {
                computes++
                "<html>new</html>"
            }
        }

        assertEquals(0, computes)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status)
        assertEquals(CommonMessageCode.SYSTEM_ERROR, error.messageCode)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
        verify(exactly = 0) { lockOperation.getSpinLock(any(), any()) }
        verify(exactly = 0) { lockOperation.getSpinLock(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("节点存在但读不到内容时回源重建并返回 HTML")
    fun nodeExistsButBlobUnreadableRebuildsAndReturnsHtml() {
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("demo")
        every { nodeService.getNodeDetail(any(), any()) } returns nodeDetail(fullPath)
        every { storageManager.loadFullArtifactInputStream(any(), null) } returns null
        stubLocks(tryLock = true)
        var computes = 0

        val result = getHtml("demo") {
            computes++
            "<html>restored</html>"
        }

        assertEquals("<html>restored</html>", result)
        assertEquals(1, computes)
        verify {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> { it.fullPath == fullPath && it.overwrite },
                any(),
                null
            )
        }
    }

    @Test
    @DisplayName("锁失败且已有文件时只返回旧文件")
    fun lockFailureWithExistingFileDoesNotCompute() {
        val html = "<html>cached</html>"
        stubCachedFile(
            PypiSimpleIndexUtils.packageCacheFullPath("demo"),
            html,
            lastModifiedDate = LocalDateTime.now().minusMinutes(2),
        )
        every { lockOperation.getLock(any()) } throws RuntimeException("redis down")

        var computes = 0
        assertEquals(html, getHtml("demo") {
            computes++
            "<html>new</html>"
        })
        assertEquals(0, computes)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("TTL 到期重建失败时保留并返回旧文件，不删除")
    fun expiredRebuildFailureKeepsStaleFile() {
        val html = "<html>stale</html>"
        val fullPath = PypiSimpleIndexUtils.packageCacheFullPath("demo")
        stubCachedFile(fullPath, html, lastModifiedDate = LocalDateTime.now().minusMinutes(2))
        stubLocks(tryLock = true)

        val result = getHtml("demo") { throw PypiSimpleNotFoundException("demo") }

        assertEquals(html, result)
        verify(exactly = 0) { nodeService.deleteNode(any()) }
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("miss 时 compute 异常原样抛出，不写文件")
    fun missPropagatesComputeExceptionWithoutStoring() {
        every { nodeService.getNodeDetail(any(), any()) } returns null
        stubLocks(spin = true)
        var computes = 0
        val compute = {
            computes++
            throw PypiSimpleNotFoundException("demo")
        }

        assertThrows<PypiSimpleNotFoundException> { getHtml("demo", compute) }
        assertThrows<PypiSimpleNotFoundException> { getHtml("demo", compute) }

        assertEquals(2, computes)
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("invalidate 只覆盖单包索引，不删文件、不扫根目录")
    fun invalidateOverwritesPackageOnly() {
        stubLocks(spin = true)
        val packagePath = PypiSimpleIndexUtils.packageCacheFullPath("My_Package")
        val rootPath = PypiSimpleIndexUtils.rootCacheFullPath()

        service.invalidate(PROJECT, REPO, "My_Package", "user", null) { "<html>pkg</html>" }

        verify {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> { it.fullPath == packagePath && it.overwrite },
                any(),
                null
            )
        }
        verify(exactly = 0) {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> { it.fullPath == rootPath },
                any(),
                null
            )
        }
        verify(exactly = 0) { nodeService.deleteNode(any()) }
    }

    @Test
    @DisplayName("invalidate 在制品不存在时才删除对应索引文件")
    fun invalidateDeletesWhenComputeNotFound() {
        stubLocks(spin = true)
        val packagePath = PypiSimpleIndexUtils.packageCacheFullPath("demo")

        service.invalidate(PROJECT, REPO, "demo", "user", null) {
            throw PypiSimpleNotFoundException("demo")
        }

        verify {
            nodeService.deleteNode(match<NodeDeleteRequest> { it.fullPath == packagePath })
        }
        verify(exactly = 0) { storageManager.storeArtifactFile(any(), any(), any()) }
    }

    @Test
    @DisplayName("invalidate 失败不抛异常")
    fun invalidateSwallowsErrors() {
        stubLocks(spin = true)
        every { storageManager.storeArtifactFile(any(), any(), any()) } throws RuntimeException("storage error")

        service.invalidate(PROJECT, REPO, "demo", "user", null) { "<html>p</html>" }
    }

    @Test
    @DisplayName("根目录 miss 写入 index.html")
    fun rootMissStoresRootIndexFile() {
        every { nodeService.getNodeDetail(any(), any()) } returns null
        stubLocks(spin = true)
        val rootPath = PypiSimpleIndexUtils.rootCacheFullPath()

        val result = getHtml(null) { "<html>root</html>" }

        assertEquals("<html>root</html>", result)
        verify {
            storageManager.storeArtifactFile(
                match<NodeCreateRequest> { it.fullPath == rootPath && it.overwrite },
                any(),
                null
            )
        }
    }

    @Test
    @DisplayName("缓存路径按 PEP 503 规范化，与 pip simple URL 对齐")
    fun packageCacheFullPathNormalizesPep503Name() {
        assertEquals(
            "/.pypi-simple-index/packages/my-package.html",
            PypiSimpleIndexUtils.packageCacheFullPath("My_Package")
        )
        assertEquals(
            "/.pypi-simple-index/packages/my-package.html",
            PypiSimpleIndexUtils.packageCacheFullPath("My.Package")
        )
        assertEquals(
            "/.pypi-simple-index/packages/my-package.html",
            PypiSimpleIndexUtils.packageCacheFullPath("my-package")
        )
        assertEquals("/.pypi-simple-index/index.html", PypiSimpleIndexUtils.rootCacheFullPath())
        assertEquals(
            "/.pypi-simple-index/index.html",
            PypiSimpleIndexUtils.cacheFullPath(null)
        )
        assertTrue(PypiSimpleIndexUtils.isSimpleIndexCacheFolder(".pypi-simple-index"))
        assertFalse(PypiSimpleIndexUtils.isSimpleIndexCacheFolder("requests"))
    }

    private fun getHtml(packageName: String?, compute: () -> String): String {
        return service.getOrCompute(PROJECT, REPO, packageName, "user", null, compute)!!
    }

    private fun stubLocks(tryLock: Boolean = true, spin: Boolean = true) {
        val lock = Any()
        every { lockOperation.getLock(any()) } returns lock
        every { lockOperation.acquireLock(any(), any()) } returns tryLock
        every { lockOperation.getSpinLock(any(), any()) } returns spin
        every { lockOperation.getSpinLock(any(), any(), any(), any()) } returns spin
        every { lockOperation.close(any(), any()) } returns Unit
    }

    private fun stubCachedFile(
        fullPath: String,
        html: String,
        lastModifiedDate: LocalDateTime = LocalDateTime.now(),
    ) {
        val node = nodeDetail(fullPath, lastModifiedDate)
        every { nodeService.getNodeDetail(any(), any()) } returns node
        every { storageManager.loadFullArtifactInputStream(any(), null) } answers { htmlStream(html) }
    }

    private fun htmlStream(html: String): ArtifactInputStream {
        return ArtifactInputStream(html.byteInputStream(), Range.full(html.length.toLong()))
    }

    private fun nodeDetail(
        fullPath: String,
        lastModifiedDate: LocalDateTime = LocalDateTime.now(),
    ): NodeDetail {
        val name = fullPath.substringAfterLast('/')
        val path = fullPath.substringBeforeLast('/') + "/"
        val info = NodeInfo(
            createdBy = "system",
            createdDate = lastModifiedDate.toString(),
            lastModifiedBy = "system",
            lastModifiedDate = lastModifiedDate.toString(),
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
