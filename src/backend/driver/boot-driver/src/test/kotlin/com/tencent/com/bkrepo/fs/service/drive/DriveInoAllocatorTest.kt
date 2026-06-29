package com.tencent.com.bkrepo.fs.service.drive

import com.tencent.bkrepo.fs.server.service.drive.DriveInoAllocator.Companion.inoFromNodeId
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Drive inode 分配")
class DriveInoAllocatorTest {

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
