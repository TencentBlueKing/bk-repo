/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.fs.service.drive

import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.service.drive.DrivePathResolveService
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class DrivePathResolveServiceTest {

    private val driveNodeDao = mock<RDriveNodeDao>()
    private val service = DrivePathResolveService(driveNodeDao)

    @Test
    fun `resolveFileNode returns file node when path exists`() = runBlocking {
        val projectId = "p1"
        val repoName = "drive-repo"
        val dirNode = directoryNode(projectId, repoName, DriveNodeQueryHelper.ROOT_INO, "docs", 10L)
        val fileNode = fileNode(projectId, repoName, 10L, "readme.txt", 20L)

        whenever(
            driveNodeDao.findCurrentNode(projectId, repoName, DriveNodeQueryHelper.ROOT_INO, "docs"),
        ).thenReturn(dirNode)
        whenever(
            driveNodeDao.findCurrentNode(projectId, repoName, 10L, "readme.txt"),
        ).thenReturn(fileNode)

        val resolved = service.resolveFileNode(projectId, repoName, "/docs/readme.txt")

        assertEquals(fileNode, resolved)
    }

    @Test
    fun `resolveFileNode returns null when parent path missing`() = runBlocking {
        val projectId = "p1"
        val repoName = "drive-repo"

        whenever(
            driveNodeDao.findCurrentNode(projectId, repoName, DriveNodeQueryHelper.ROOT_INO, "missing"),
        ).thenReturn(null)

        val resolved = service.resolveFileNode(projectId, repoName, "/missing/file.txt")

        assertNull(resolved)
    }

    @Test
    fun `resolveFileNode returns null when target is directory`() = runBlocking {
        val projectId = "p1"
        val repoName = "drive-repo"
        val dirNode = directoryNode(projectId, repoName, DriveNodeQueryHelper.ROOT_INO, "docs", 10L)

        whenever(
            driveNodeDao.findCurrentNode(projectId, repoName, DriveNodeQueryHelper.ROOT_INO, "docs"),
        ).thenReturn(dirNode)

        val resolved = service.resolveFileNode(projectId, repoName, "/docs")

        assertNull(resolved)
    }

    private fun directoryNode(
        projectId: String,
        repoName: String,
        parent: Long,
        name: String,
        ino: Long,
    ): TDriveNode {
        return TDriveNode(
            createdBy = "user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
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

    private fun fileNode(
        projectId: String,
        repoName: String,
        parent: Long,
        name: String,
        ino: Long,
    ): TDriveNode {
        return TDriveNode(
            createdBy = "user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
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
