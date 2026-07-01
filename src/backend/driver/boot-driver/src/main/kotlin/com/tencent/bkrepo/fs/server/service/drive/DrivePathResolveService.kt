/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import org.springframework.stereotype.Service

@Service
class DrivePathResolveService(
    private val driveNodeDao: RDriveNodeDao,
) {

    suspend fun resolveFileNode(projectId: String, repoName: String, fullPath: String): TDriveNode? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        val parentPath = PathUtils.resolveParent(normalizedPath)
        val fileName = PathUtils.resolveName(normalizedPath)
        val parentIno = resolveParentIno(projectId, repoName, parentPath) ?: return null
        val node = driveNodeDao.findCurrentNode(projectId, repoName, parentIno, fileName) ?: return null
        return if (node.type == TYPE_FILE) node else null
    }

    private suspend fun resolveParentIno(projectId: String, repoName: String, parentPath: String): Long? {
        if (PathUtils.isRoot(parentPath)) {
            return DriveNodeQueryHelper.ROOT_INO
        }
        val segments = parentPath.removePrefix(PathUtils.ROOT)
            .split(PathUtils.UNIX_SEPARATOR)
            .filter { it.isNotBlank() }
        var parentIno = DriveNodeQueryHelper.ROOT_INO
        for (segment in segments) {
            val existing = driveNodeDao.findCurrentNode(projectId, repoName, parentIno, segment) ?: return null
            if (existing.type != TYPE_DIRECTORY) {
                return null
            }
            parentIno = existing.ino
        }
        return parentIno
    }
}
