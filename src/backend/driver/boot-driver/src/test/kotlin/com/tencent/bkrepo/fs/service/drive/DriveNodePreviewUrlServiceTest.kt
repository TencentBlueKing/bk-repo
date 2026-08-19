package com.tencent.bkrepo.fs.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.service.drive.DriveFilePathInfo
import com.tencent.bkrepo.fs.server.service.drive.DriveNodePreviewUrlService
import com.tencent.bkrepo.fs.server.service.drive.DrivePathResolveService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class DriveNodePreviewUrlServiceTest {

    private val drivePathResolveService = mock<DrivePathResolveService>()
    private val driveProperties = DriveProperties().apply { domain = "https://bkrepo.example.com" }
    private val service = DriveNodePreviewUrlService(drivePathResolveService, driveProperties)

    @Test
    fun `IMATE_AGENT returns imate_artifact url with metadata name and type`() = runBlocking {
        mockFilePath(
            INO,
            "/docs/readme.txt",
            metadata = listOf(
                TMetadata(key = DriveNodePreviewUrlService.METADATA_ARTIFACT_NAME, value = "周报汇总"),
                TMetadata(key = DriveNodePreviewUrlService.METADATA_ARTIFACT_TYPE, value = "table"),
            ),
        )

        val url = service.buildPreviewUrl(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = INO,
            type = DriveNodePreviewUrlService.TYPE_IMATE_AGENT,
        )

        assertEquals(
            "imate_artifact://$INO?name=%E5%91%A8%E6%8A%A5%E6%B1%87%E6%80%BB&type=table",
            url,
        )
    }

    @Test
    fun `IMATE_AGENT falls back to file name and other type`() = runBlocking {
        mockFilePath(INO, "/docs/readme.txt")

        val url = service.buildPreviewUrl(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = INO,
            type = DriveNodePreviewUrlService.TYPE_IMATE_AGENT,
        )

        assertEquals("imate_artifact://$INO?name=readme.txt&type=other", url)
    }

    @Test
    fun `IMATE_AGENT keeps video and audio artifact types`() = runBlocking {
        mockFilePath(
            INO,
            "/clips/demo.mp4",
            metadata = listOf(
                TMetadata(key = DriveNodePreviewUrlService.METADATA_ARTIFACT_TYPE, value = "video"),
            ),
        )
        val videoUrl = service.buildPreviewUrl(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = INO,
            type = DriveNodePreviewUrlService.TYPE_IMATE_AGENT,
        )
        assertEquals("imate_artifact://$INO?name=demo.mp4&type=video", videoUrl)

        mockFilePath(
            INO,
            "/sound/demo.mp3",
            metadata = listOf(
                TMetadata(key = DriveNodePreviewUrlService.METADATA_ARTIFACT_TYPE, value = "audio"),
            ),
        )
        val audioUrl = service.buildPreviewUrl(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = INO,
            type = DriveNodePreviewUrlService.TYPE_IMATE_AGENT,
        )
        assertEquals("imate_artifact://$INO?name=demo.mp3&type=audio", audioUrl)
    }

    @Test
    fun `IMATE_AGENT unknown artifact type becomes other`() = runBlocking {
        mockFilePath(
            INO,
            "/a.xlsx",
            metadata = listOf(
                TMetadata(key = DriveNodePreviewUrlService.METADATA_ARTIFACT_TYPE, value = "unknown-type"),
            ),
        )

        val url = service.buildPreviewUrl(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = INO,
            type = DriveNodePreviewUrlService.TYPE_IMATE_AGENT,
        )

        assertEquals("imate_artifact://$INO?name=a.xlsx&type=other", url)
    }

    @Test
    fun `IMATE_CLIENT returns ui preview url when file exists`() = runBlocking {
        mockFilePath(INO, "/docs/readme.txt")

        val url = service.buildPreviewUrl(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = INO,
            type = DriveNodePreviewUrlService.TYPE_IMATE_CLIENT,
        )

        assertEquals(
            "https://bkrepo.example.com/ui/$PROJECT_ID/filePreview/local/0/$REPO_NAME/docs/readme.txt",
            url,
        )
    }

    @Test
    fun `unknown or missing type falls back to client url`() = runBlocking {
        mockFilePath(INO, "/a.png")

        val unknown = service.buildPreviewUrl(PROJECT_ID, REPO_NAME, INO, "OTHER")
        val missing = service.buildPreviewUrl(PROJECT_ID, REPO_NAME, INO, null)
        val wrongCase = service.buildPreviewUrl(PROJECT_ID, REPO_NAME, INO, "imate_agent")

        val expected = "https://bkrepo.example.com/ui/$PROJECT_ID/filePreview/local/0/$REPO_NAME/a.png"
        assertEquals(expected, unknown)
        assertEquals(expected, missing)
        assertEquals(expected, wrongCase)
    }

    @Test
    fun `throws when node missing or directory`() = runBlocking {
        whenever(
            drivePathResolveService.resolveFilePathInfoByIno(any(), any(), any(), anyOrNull()),
        ).thenReturn(null)

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlocking {
                service.buildPreviewUrl(
                    projectId = PROJECT_ID,
                    repoName = REPO_NAME,
                    ino = INO,
                    type = DriveNodePreviewUrlService.TYPE_IMATE_CLIENT,
                )
            }
        }
        assertEquals(ArtifactMessageCode.NODE_NOT_FOUND, exception.messageCode)
    }

    @Test
    fun `throws when domain blank for client url`() = runBlocking {
        mockFilePath(INO, "/a.png")
        driveProperties.domain = "  "

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlocking {
                service.buildPreviewUrl(
                    projectId = PROJECT_ID,
                    repoName = REPO_NAME,
                    ino = INO,
                    type = DriveNodePreviewUrlService.TYPE_IMATE_CLIENT,
                )
            }
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID, exception.messageCode)
    }

    private suspend fun mockFilePath(
        ino: Long,
        fullPath: String,
        metadata: List<TMetadata>? = null,
    ) {
        val fileName = fullPath.substringAfterLast('/')
        val node = TDriveNode(
            createdBy = "user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            parent = 1L,
            name = fileName,
            ino = ino,
            mode = 0,
            type = TYPE_FILE,
            size = 1,
            nlink = 1,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
            metadata = metadata?.toMutableList(),
        )
        whenever(
            drivePathResolveService.resolveFilePathInfoByIno(
                eq(PROJECT_ID),
                eq(REPO_NAME),
                eq(ino),
                anyOrNull(),
            ),
        ).thenReturn(DriveFilePathInfo(fullPath = fullPath, node = node))
    }

    companion object {
        private const val PROJECT_ID = "p1"
        private const val REPO_NAME = "drive"
        private const val INO = 100L
    }
}
