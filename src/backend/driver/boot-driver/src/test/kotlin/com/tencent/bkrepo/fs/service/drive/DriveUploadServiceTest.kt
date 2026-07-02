package com.tencent.bkrepo.fs.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.request.drive.DriveBlockWriteRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeCreateRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeDeleteRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeUploadRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveBlockNode
import com.tencent.bkrepo.fs.server.response.drive.DriveNode
import com.tencent.bkrepo.fs.server.service.drive.DriveFileOperationService
import com.tencent.bkrepo.fs.server.service.drive.DriveInoAllocator
import com.tencent.bkrepo.fs.server.service.drive.DriveNodeService
import com.tencent.bkrepo.fs.server.service.drive.DriveRepositoryInitService
import com.tencent.bkrepo.fs.server.service.drive.DriveUploadService
import com.tencent.bkrepo.fs.server.storage.CoArtifactFile
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@DisplayName("Drive 完整文件上传")
class DriveUploadServiceTest {

    private val driveNodeService: DriveNodeService = mock()
    private val driveNodeDao: RDriveNodeDao = mock()
    private val driveFileOperationService: DriveFileOperationService = mock()
    private val driveRepositoryInitService: DriveRepositoryInitService = mock()
    private val driveInoAllocator: DriveInoAllocator = mock()

    private val driveUploadService = DriveUploadService(
        driveNodeService = driveNodeService,
        driveNodeDao = driveNodeDao,
        driveFileOperationService = driveFileOperationService,
        driveRepositoryInitService = driveRepositoryInitService,
        driveInoAllocator = driveInoAllocator,
    )

    @Test
    fun `should reject existing file before body read when overwrite is false`() {
        val existingNode = existingFileNode()
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/a.txt",
            overwrite = false,
        )

        runBlocking {
            whenever(driveRepositoryInitService.ensureInitialized(any(), any(), any())).thenReturn(Unit)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DriveNodeQueryHelper.ROOT_INO, "a.txt"),
            ).thenReturn(existingNode)
        }

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlocking {
                driveUploadService.checkUploadTargetBeforeBody(uploadRequest, USER_ID)
            }
        }

        assertEquals(ArtifactMessageCode.NODE_EXISTED, exception.messageCode)
    }

    @Test
    fun `should allow existing file before body read when overwrite is true`() {
        val existingNode = existingFileNode()
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/a.txt",
            overwrite = true,
        )

        runBlocking {
            whenever(driveRepositoryInitService.ensureInitialized(any(), any(), any())).thenReturn(Unit)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DriveNodeQueryHelper.ROOT_INO, "a.txt"),
            ).thenReturn(existingNode)
        }

        runBlocking {
            driveUploadService.checkUploadTargetBeforeBody(uploadRequest, USER_ID)
        }
    }

    @Test
    fun `should skip pre-check when parent directory path does not exist`() {
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/missing-dir/a.txt",
            overwrite = false,
        )

        runBlocking {
            whenever(driveRepositoryInitService.ensureInitialized(any(), any(), any())).thenReturn(Unit)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DriveNodeQueryHelper.ROOT_INO, "missing-dir"),
            ).thenReturn(null)
        }

        runBlocking {
            driveUploadService.checkUploadTargetBeforeBody(uploadRequest, USER_ID)
        }
    }

    @Test
    fun `should reject sha256 mismatch`() {
        val artifactFile = artifactFile(sha256 = "actual", size = 4)
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/a.txt",
            expectedSha256 = "expected",
        )

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlocking {
                driveUploadService.uploadCompleteFile(uploadRequest, artifactFile, null, USER_ID)
            }
        }

        assertEquals(ArtifactMessageCode.DIGEST_CHECK_FAILED, exception.messageCode)
        runBlocking {
            verify(driveRepositoryInitService, never()).ensureInitialized(any(), any(), any())
        }
    }

    @Test
    fun `should reject existing file when overwrite is false`() {
        val existingNode = existingFileNode()
        val artifactFile = artifactFile(sha256 = "abc", size = 4)
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/a.txt",
            overwrite = false,
        )

        runBlocking {
            whenever(driveRepositoryInitService.ensureInitialized(any(), any(), any())).thenReturn(Unit)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DriveNodeQueryHelper.ROOT_INO, "a.txt"),
            ).thenReturn(existingNode)
        }

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlocking {
                driveUploadService.uploadCompleteFile(uploadRequest, artifactFile, null, USER_ID)
            }
        }

        assertEquals(ArtifactMessageCode.NODE_EXISTED, exception.messageCode)
        runBlocking {
            verify(driveNodeService, never()).createNode(any<DriveNodeCreateRequest>(), anyOrNull(), anyOrNull())
            verify(
                driveFileOperationService, never()
            ).write(any<CoArtifactFile>(), any<DriveBlockWriteRequest>(), any())
        }
    }

    @Test
    fun `should overwrite existing file when overwrite is true`() {
        val existingNode = existingFileNode()
        val artifactFile = artifactFile(sha256 = "abc", size = 4)
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/a.txt",
            overwrite = true,
        )
        val newIno = 2002L
        val createdNode = driveNode(ino = newIno)

        runBlocking {
            whenever(driveRepositoryInitService.ensureInitialized(any(), any(), any())).thenReturn(Unit)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DriveNodeQueryHelper.ROOT_INO, "a.txt"),
            ).thenReturn(existingNode)
            whenever(driveInoAllocator.allocate(PROJECT_ID, REPO_NAME)).thenReturn(newIno)
            whenever(driveNodeService.delete(any<DriveNodeDeleteRequest>(), anyOrNull(), anyOrNull()))
                .thenReturn(driveNode(ino = existingNode.ino))
            whenever(driveNodeService.createNode(any<DriveNodeCreateRequest>(), anyOrNull(), anyOrNull()))
                .thenReturn(createdNode)
            whenever(
                driveFileOperationService.write(any<CoArtifactFile>(), any<DriveBlockWriteRequest>(), any()),
            ).thenReturn(mock<DriveBlockNode>())
        }

        val result = runBlocking {
            driveUploadService.uploadCompleteFile(uploadRequest, artifactFile, null, USER_ID)
        }

        assertEquals(newIno, result.ino)
        runBlocking {
            verify(driveNodeService).delete(any<DriveNodeDeleteRequest>(), anyOrNull(), anyOrNull())
            verify(driveNodeService).createNode(any<DriveNodeCreateRequest>(), anyOrNull(), anyOrNull())
            verify(driveFileOperationService).write(any<CoArtifactFile>(), any<DriveBlockWriteRequest>(), any())
            verify(driveNodeService, never()).update(any(), anyOrNull(), anyOrNull())
        }
    }

    @Test
    fun `should reuse concurrently created parent directory`() {
        val directoryNode = existingDirectoryNode()
        val artifactFile = artifactFile(sha256 = "abc", size = 4)
        val uploadRequest = DriveNodeUploadRequest(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/new-dir/a.txt",
            overwrite = false,
        )
        val fileIno = 2002L
        val createdNode = driveNode(ino = fileIno)

        runBlocking {
            whenever(driveRepositoryInitService.ensureInitialized(any(), any(), any())).thenReturn(Unit)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DriveNodeQueryHelper.ROOT_INO, "new-dir"),
            ).thenReturn(null, directoryNode)
            whenever(
                driveNodeDao.findCurrentNode(PROJECT_ID, REPO_NAME, DIRECTORY_INO, "a.txt"),
            ).thenReturn(null)
            whenever(driveInoAllocator.allocate(PROJECT_ID, REPO_NAME)).thenReturn(2001L, fileIno)
            whenever(driveNodeService.createNode(any<DriveNodeCreateRequest>(), anyOrNull(), anyOrNull()))
                .thenThrow(ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, "new-dir"))
                .thenReturn(createdNode)
            whenever(
                driveFileOperationService.write(any<CoArtifactFile>(), any<DriveBlockWriteRequest>(), any()),
            ).thenReturn(mock<DriveBlockNode>())
        }

        val result = runBlocking {
            driveUploadService.uploadCompleteFile(uploadRequest, artifactFile, null, USER_ID)
        }

        assertEquals(fileIno, result.ino)
        runBlocking {
            verify(driveFileOperationService).write(any<CoArtifactFile>(), any<DriveBlockWriteRequest>(), any())
        }
    }

    private fun existingFileNode(): TDriveNode {
        val now = LocalDateTime.now()
        return TDriveNode(
            createdBy = USER_ID,
            createdDate = now,
            lastModifiedBy = USER_ID,
            lastModifiedDate = now,
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = 1001L,
            realIno = 1001L,
            parent = DriveNodeQueryHelper.ROOT_INO,
            name = "a.txt",
            size = 1,
            mode = 0,
            type = TYPE_FILE,
            nlink = 1,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
            mtime = 0,
            ctime = 0,
            atime = 0,
        )
    }

    private fun existingDirectoryNode(): TDriveNode {
        val now = LocalDateTime.now()
        return TDriveNode(
            id = "directory-id",
            createdBy = USER_ID,
            createdDate = now,
            lastModifiedBy = USER_ID,
            lastModifiedDate = now,
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = DIRECTORY_INO,
            realIno = DIRECTORY_INO,
            parent = DriveNodeQueryHelper.ROOT_INO,
            name = "new-dir",
            size = 0,
            mode = 0,
            type = TYPE_DIRECTORY,
            nlink = 2,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
            mtime = 0,
            ctime = 0,
            atime = 0,
        )
    }

    private fun driveNode(ino: Long): DriveNode {
        val now = LocalDateTime.now()
        return DriveNode(
            id = "node-id",
            createdBy = USER_ID,
            createdDate = now,
            lastModifiedBy = USER_ID,
            lastModifiedDate = now,
            mtime = 0,
            ctime = 0,
            atime = 0,
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            ino = ino,
            realIno = ino,
            parent = DriveNodeQueryHelper.ROOT_INO,
            name = "a.txt",
            size = 4,
            mode = 0,
            type = TYPE_FILE,
            nlink = 1,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
        )
    }

    private fun artifactFile(sha256: String, size: Long): CoArtifactFile {
        val artifactFile = mock<CoArtifactFile>()
        whenever(artifactFile.getFileSha256()).thenReturn(sha256)
        whenever(artifactFile.getSize()).thenReturn(size)
        return artifactFile
    }

    companion object {
        private const val PROJECT_ID = "demo"
        private const val REPO_NAME = "drive-local"
        private const val USER_ID = "admin"
        private const val DIRECTORY_INO = 1000L
    }
}
