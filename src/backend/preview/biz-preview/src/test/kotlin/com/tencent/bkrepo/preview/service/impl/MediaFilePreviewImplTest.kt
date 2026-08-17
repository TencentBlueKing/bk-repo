package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewSystemException
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.FileType
import com.tencent.bkrepo.preview.service.FileTransferService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("媒体文件预览")
class MediaFilePreviewImplTest {

    private val fileTransferService = mockk<FileTransferService>(relaxed = true)
    private val mediaFilePreview = MediaFilePreviewImpl(fileTransferService)

    @Test
    fun `remote media preview is not supported`() {
        val fileAttribute = FileAttribute(
            storageType = 1,
            url = "https://example.com/demo.mp4",
            fileName = "demo.mp4",
            suffix = "mp4",
            type = FileType.MEDIA
        )

        val exception = assertThrows(PreviewSystemException::class.java) {
            mediaFilePreview.filePreviewHandle(fileAttribute)
        }
        assertEquals(PreviewMessageCode.PREVIEW_FILE_NOT_SUPPORT_ERROR, exception.messageCode)
        verify(exactly = 0) { fileTransferService.sendOriginalFileAsResponse(any()) }
    }

    @Test
    fun `local media preview streams original artifact`() {
        val fileAttribute = FileAttribute(
            storageType = 0,
            projectId = "p1",
            repoName = "generic-local",
            artifactUri = "/video/demo.mp4",
            fileName = "demo.mp4",
            suffix = "mp4",
            type = FileType.MEDIA,
            size = 2L * 1024 * 1024 * 1024
        )

        mediaFilePreview.filePreviewHandle(fileAttribute)

        verify(exactly = 1) { fileTransferService.sendOriginalFileAsResponse(fileAttribute) }
        verify(exactly = 0) { fileTransferService.download(any()) }
        verify(exactly = 0) { fileTransferService.sendFileAsResponse(any(), any()) }
    }
}
