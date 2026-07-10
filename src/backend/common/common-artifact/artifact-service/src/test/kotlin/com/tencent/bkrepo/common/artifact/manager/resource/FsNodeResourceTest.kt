package com.tencent.bkrepo.common.artifact.manager.resource

import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.Constant.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.util.Constant.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.util.Constant.UT_USER
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.InputStream

class FsNodeResourceTest {
    private val blockNodeService = mock<BlockNodeService>()
    private val storageService = mock<StorageService>()
    private val storageCredentialService = mock<StorageCredentialService>()
    private val currentCredentials = FileSystemCredentials(key = CURRENT_STORAGE_KEY)
    private val oldCredentials = FileSystemCredentials(key = OLD_STORAGE_KEY)
    private val range = Range.full(BLOCK_SIZE)
    private val block = RegionResource(BLOCK_SHA256, 0, BLOCK_SIZE, 0, BLOCK_SIZE)
    private val secondBlock = RegionResource(SECOND_BLOCK_SHA256, BLOCK_SIZE, BLOCK_SIZE, 0, BLOCK_SIZE)

    @BeforeEach
    fun beforeEach() {
        mockRepoDetail()
        whenever(blockNodeService.info(any(), eq(range))).thenReturn(listOf(block))
        whenever(
            storageService.load(any<List<RegionResource>>(), eq(range), any<(RegionResource) -> InputStream>())
        ).thenAnswer { invocation: InvocationOnMock ->
            val blocks = invocation.getArgument<List<RegionResource>>(0)
            val loader = invocation.getArgument<(RegionResource) -> InputStream>(2)
            blocks.map { loader(it) }.lastOrNull()
        }
    }

    @AfterEach
    fun afterEach() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `should load block from current storage first`() {
        val currentInputStream = artifactInputStream()
        whenever(storageService.loadResource(block, currentCredentials)).thenReturn(currentInputStream)

        val inputStream = fsNodeResource().getArtifactInputStream()

        assertSame(currentInputStream, inputStream)
        verify(storageCredentialService, never()).findByKey(OLD_STORAGE_KEY)
        verify(storageService, never()).loadResource(block, oldCredentials)
    }

    @Test
    fun `should load block from old storage during migration`() {
        val oldInputStream = artifactInputStream()
        whenever(storageService.loadResource(block, currentCredentials)).thenReturn(null)
        whenever(storageCredentialService.findByKey(OLD_STORAGE_KEY)).thenReturn(oldCredentials)
        whenever(storageService.loadResource(block, oldCredentials)).thenReturn(oldInputStream)

        val inputStream = fsNodeResource().getArtifactInputStream()

        assertSame(oldInputStream, inputStream)
        verify(storageCredentialService).findByKey(OLD_STORAGE_KEY)
        verify(storageService).loadResource(block, oldCredentials)
    }

    @Test
    fun `should cache old storage credentials when loading multiple blocks`() {
        mockRepoDetail(CACHE_OLD_STORAGE_KEY)
        whenever(blockNodeService.info(any(), eq(range))).thenReturn(listOf(block, secondBlock))
        whenever(storageService.loadResource(any<RegionResource>(), eq(currentCredentials))).thenReturn(null)
        whenever(storageCredentialService.findByKey(CACHE_OLD_STORAGE_KEY)).thenReturn(oldCredentials)
        whenever(
            storageService.loadResource(
                any<RegionResource>(),
                eq(oldCredentials)
            )
        ).thenAnswer { artifactInputStream() }

        fsNodeResource().getArtifactInputStream()

        verify(storageCredentialService, times(1)).findByKey(CACHE_OLD_STORAGE_KEY)
        verify(storageService).loadResource(block, oldCredentials)
        verify(storageService).loadResource(secondBlock, oldCredentials)
    }

    private fun fsNodeResource() = FsNodeResource(
        node = nodeInfo(),
        blockNodeService = blockNodeService,
        range = range,
        storageService = storageService,
        storageCredentials = currentCredentials,
        storageCredentialService = storageCredentialService,
    )

    private fun nodeInfo() = NodeInfo(
        createdBy = UT_USER,
        createdDate = "2026-07-02T16:00:00",
        lastModifiedBy = UT_USER,
        lastModifiedDate = "2026-07-02T16:00:00",
        folder = false,
        path = "/a/b",
        name = "c.txt",
        fullPath = "/a/b/c.txt",
        size = BLOCK_SIZE,
        sha256 = FAKE_SHA256,
        projectId = UT_PROJECT_ID,
        repoName = UT_REPO_NAME,
    )

    private fun mockRepoDetail(oldCredentialsKey: String = OLD_STORAGE_KEY) {
        val request = MockHttpServletRequest()
        request.setAttribute(REPO_KEY, repositoryDetail(oldCredentialsKey))
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    private fun repositoryDetail(oldCredentialsKey: String) = RepositoryDetail(
        projectId = UT_PROJECT_ID,
        name = UT_REPO_NAME,
        type = RepositoryType.GENERIC,
        category = RepositoryCategory.LOCAL,
        public = false,
        description = null,
        configuration = RepositoryConfiguration(),
        storageCredentials = currentCredentials,
        oldCredentialsKey = oldCredentialsKey,
        createdBy = UT_USER,
        createdDate = "2026-07-02T16:00:00",
        lastModifiedBy = UT_USER,
        lastModifiedDate = "2026-07-02T16:00:00",
        quota = null,
        used = null,
    )

    private fun artifactInputStream(): ArtifactInputStream {
        val data = ByteArray(BLOCK_SIZE.toInt()) { 1 }
        return ArtifactInputStream(data.inputStream(), range)
    }

    companion object {
        private const val CURRENT_STORAGE_KEY = "current-storage-key"
        private const val OLD_STORAGE_KEY = "old-storage-key"
        private const val CACHE_OLD_STORAGE_KEY = "cache-old-storage-key"
        private const val BLOCK_SHA256 = "ab7df1f97d2bf53f4cbf66cd59fd1f8a7250a1569b3ea9c582798a5042567572"
        private const val SECOND_BLOCK_SHA256 = "d105b4f8ef7b10ce51290f110c38efad287fa0ac315c13160aab2b03380ba67d"
        private const val BLOCK_SIZE = 16L
    }
}
