package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_FILE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DriveNodePathHelperTest {

    @Test
    fun `resolveFileNode returns file node when path exists`() {
        val dirNode = directoryNode(DriveNodePathHelper.ROOT_INO, "docs", 10L)
        val fileNode = fileNode(10L, "readme.txt", 20L)
        val nodes = mapOf(
            DriveNodePathHelper.ROOT_INO to "docs" to dirNode,
            10L to "readme.txt" to fileNode,
        )

        val resolved = DriveNodePathHelper.resolveFileNode("p1", "repo", "/docs/readme.txt") { parent, name ->
            nodes[parent to name]
        }

        assertEquals(fileNode, resolved)
    }

    @Test
    fun `resolveFileNode returns null when parent path missing`() {
        val resolved = DriveNodePathHelper.resolveFileNode("p1", "repo", "/missing/file.txt") { _, _ -> null }
        assertNull(resolved)
    }

    @Test
    fun `resolveFileNode returns null when target is directory`() {
        val dirNode = directoryNode(DriveNodePathHelper.ROOT_INO, "docs", 10L)
        val resolved = DriveNodePathHelper.resolveFileNode("p1", "repo", "/docs") { parent, name ->
            if (parent == DriveNodePathHelper.ROOT_INO && name == "docs") dirNode else null
        }
        assertNull(resolved)
    }

    private fun directoryNode(parent: Long, name: String, ino: Long): TDriveNode {
        return TDriveNode(
            createdBy = "user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = "p1",
            repoName = "repo",
            parent = parent,
            name = name,
            ino = ino,
            mode = 0,
            type = TYPE_DIRECTORY,
            size = 0,
            nlink = 1,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
        )
    }

    private fun fileNode(parent: Long, name: String, ino: Long): TDriveNode {
        return TDriveNode(
            createdBy = "user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = "p1",
            repoName = "repo",
            parent = parent,
            name = name,
            ino = ino,
            mode = 0,
            type = TYPE_FILE,
            size = 1024,
            nlink = 1,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
        )
    }
}
