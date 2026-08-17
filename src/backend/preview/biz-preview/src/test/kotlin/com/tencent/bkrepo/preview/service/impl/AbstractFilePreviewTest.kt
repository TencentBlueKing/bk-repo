package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.common.artifact.properties.EnableMultiTenantProperties
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.pojo.DownloadResult
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.FileType
import com.tencent.bkrepo.preview.service.FileTransferService
import com.tencent.bkrepo.preview.service.cache.impl.PreviewFileCacheServiceImpl
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("通用文件预览")
class AbstractFilePreviewTest {

    private val config = PreviewConfig()
    private val fileTransferService = mockk<FileTransferService>()
    private val previewFileCacheService = mockk<PreviewFileCacheServiceImpl>()
    private val nodeService = mockk<NodeService>()
    private val repositoryService = mockk<RepositoryService>()
    private val enableMultiTenant = mockk<EnableMultiTenantProperties>()
    private lateinit var preview: PdfFilePreviewImpl

    @BeforeEach
    fun setUp() {
        every { enableMultiTenant.enabled } returns false
        preview = PdfFilePreviewImpl(config, fileTransferService, previewFileCacheService, nodeService)
        preview.repositoryService = repositoryService
        preview.enableMultiTenant = enableMultiTenant
    }

    @Test
    fun `remote preview does not npe when resolving drive repo`() {
        val fileAttribute = FileAttribute(
            storageType = 1,
            url = "https://example.com/demo.pdf",
            fileName = "demo.pdf",
            suffix = "pdf",
            type = FileType.PDF
        )
        every { fileTransferService.download(fileAttribute) } returns DownloadResult(
            code = DownloadResult.CODE_SUCCESS,
            filePath = "/tmp/demo.pdf",
            md5 = "md5",
            size = 1024
        )
        every { previewFileCacheService.getCache(any(), any(), any()) } returns null
        val nodeDetail = mockk<NodeDetail>()
        every { nodeDetail.projectId } returns "bk-repo"
        every { nodeDetail.repoName } returns "convert"
        every { nodeDetail.fullPath } returns "/preview/demo.pdf"
        every { fileTransferService.upload(fileAttribute, any()) } returns nodeDetail
        every { previewFileCacheService.createCache(any()) } returns mockk()
        every { fileTransferService.sendFileAsResponse(any(), any()) } returns Unit

        preview.filePreviewHandle(fileAttribute)

        verify(exactly = 0) { repositoryService.getRepoDetail(any(), any(), any()) }
        verify(exactly = 1) { fileTransferService.sendFileAsResponse(fileAttribute, any()) }
    }
}
