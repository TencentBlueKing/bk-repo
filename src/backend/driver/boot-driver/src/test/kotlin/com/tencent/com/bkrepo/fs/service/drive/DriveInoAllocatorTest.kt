package com.tencent.com.bkrepo.fs.service.drive

import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.service.drive.DriveInoAllocator
import com.tencent.bkrepo.fs.server.service.drive.DriveInoAllocator.Companion.inoFromNodeId
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException

@DisplayName("Drive inode 分配")
class DriveInoAllocatorTest {

    private val driveNodeDao: RDriveNodeDao = mock()
    private val allocator = DriveInoAllocator(driveNodeDao)

    // ===== inoFromNodeId 静态方法测试 =====

    @Nested
    @DisplayName("inoFromNodeId")
    inner class InoFromNodeId {

        @Test
        fun `should derive ino from node id deterministically`() {
            val nodeId = "674a1b2c3d4e5f6789012345"
            assertEquals(inoFromNodeId(nodeId), inoFromNodeId(nodeId))
            assertTrue(inoFromNodeId(nodeId) >= DriveNodeQueryHelper.ROOT_INO)
        }

        @Test
        fun `should map different node ids to different ino values`() {
            val first = inoFromNodeId("674a1b2c3d4e5f6789012345")
            val second = inoFromNodeId("674a1b2c3d4e5f6789012346")
            assertNotEquals(first, second)
        }
    }

    // ===== allocate 方法测试 =====

    @Nested
    @DisplayName("allocate")
    inner class Allocate {

        @Test
        fun `should return ino when ino does not exist`() {
            val projectId = "demo"
            val repoName = "drive-local"

            runBlocking {
                whenever(driveNodeDao.existsIno(any(), any(), any())).thenReturn(false)
                val ino = allocator.allocate(projectId, repoName)
                assertTrue(ino >= DriveNodeQueryHelper.ROOT_INO)
                assertTrue(ino < Long.MAX_VALUE)
                verify(driveNodeDao, times(1)).existsIno(projectId, repoName, ino)
            }
        }

        @Test
        fun `should retry when first ino already exists`() {
            val projectId = "demo"
            val repoName = "drive-local"

            runBlocking {
                whenever(driveNodeDao.existsIno(any(), any(), any()))
                    .thenReturn(true)
                    .thenReturn(false)
                val ino = allocator.allocate(projectId, repoName)
                assertTrue(ino >= DriveNodeQueryHelper.ROOT_INO)
                verify(driveNodeDao, times(2)).existsIno(any(), any(), any())
            }
        }

        @Test
        fun `should throw DuplicateKeyException after max retries`() {
            val projectId = "demo"
            val repoName = "drive-local"

            runBlocking {
                whenever(driveNodeDao.existsIno(any(), any(), any())).thenReturn(true)
                try {
                    allocator.allocate(projectId, repoName)
                    assertTrue(false, "Expected DuplicateKeyException was not thrown")
                } catch (e: DuplicateKeyException) {
                    assertTrue(e.message!!.contains(projectId))
                    assertTrue(e.message!!.contains(repoName))
                }
                // MAX_RETRY = 5
                verify(driveNodeDao, times(5)).existsIno(any(), any(), any())
            }
        }
    }
}
