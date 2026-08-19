package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.service.drive.DriveFileReadService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.fs.server.pojo.DriveFileBlockInfo
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Drive 节点预览下载")
class DrivePreviewDownloadServiceTest {

    private val driveFileReadService = mockk<DriveFileReadService>()
    private val storageService = mockk<StorageService>()
    private val service = DrivePreviewDownloadService(driveFileReadService, storageService)

    @Test
    fun `missing drive node is not found`() {
        every { driveFileReadService.getFileBlockInfo("p", "r", "/a.mp4") } returns null

        val exception = assertThrows(PreviewNotFoundException::class.java) {
            service.loadArtifactInputStream("p", "r", "/a.mp4", null)
        }
        assertEquals(PreviewMessageCode.PREVIEW_FILE_NOT_FOUND, exception.messageCode)
    }

    @Test
    fun `explicit range is forwarded to storage load`() {
        val blocks = listOf(RegionResource("digest", 0, 100, 0, 100))
        every { driveFileReadService.getFileBlockInfo("p", "r", "/a.mp4") } returns DriveFileBlockInfo(
            fullPath = "/a.mp4",
            fileName = "a.mp4",
            size = 100,
            blocks = blocks,
        )
        val stream = mockk<ArtifactInputStream>()
        val range = Range(0, 0, 100)
        every { storageService.load(blocks, range, null) } returns stream

        val result = service.loadArtifactInputStream("p", "r", "/a.mp4", null, range)

        assertSame(stream, result)
    }

    @Test
    fun `omitted range loads the full drive file`() {
        val blocks = listOf(RegionResource("digest", 0, 100, 0, 100))
        every { driveFileReadService.getFileBlockInfo("p", "r", "/a.mp4") } returns DriveFileBlockInfo(
            fullPath = "/a.mp4",
            fileName = "a.mp4",
            size = 100,
            blocks = blocks,
        )
        val stream = mockk<ArtifactInputStream>()
        val captured = slot<Range>()
        every { storageService.load(blocks, capture(captured), null) } returns stream

        val result = service.loadArtifactInputStream("p", "r", "/a.mp4", null)

        assertSame(stream, result)
        assertTrue(captured.captured.isFullContent())
        assertEquals(100L, captured.captured.total)
    }

    @Test
    fun `missing storage stream is not found`() {
        val blocks = listOf(RegionResource("digest", 0, 100, 0, 100))
        every { driveFileReadService.getFileBlockInfo("p", "r", "/a.mp4") } returns DriveFileBlockInfo(
            fullPath = "/a.mp4",
            fileName = "a.mp4",
            size = 100,
            blocks = blocks,
        )
        every { storageService.load(blocks, any<Range>(), null) } returns null

        val exception = assertThrows(PreviewNotFoundException::class.java) {
            service.loadArtifactInputStream("p", "r", "/a.mp4", null)
        }
        assertEquals(PreviewMessageCode.PREVIEW_FILE_NOT_FOUND, exception.messageCode)
    }
}
