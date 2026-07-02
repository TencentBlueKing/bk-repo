package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_FILE

object DriveNodePathHelper {
    const val ROOT_INO = 2L

    fun resolveFileNode(
        projectId: String,
        repoName: String,
        fullPath: String,
        findCurrentNode: (parent: Long, name: String) -> TDriveNode?,
    ): TDriveNode? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        val parentPath = PathUtils.resolveParent(normalizedPath)
        val fileName = PathUtils.resolveName(normalizedPath)
        val parentIno = resolveParentIno(projectId, repoName, parentPath, findCurrentNode) ?: return null
        val node = findCurrentNode(parentIno, fileName) ?: return null
        return if (node.type == TYPE_FILE) node else null
    }

    suspend fun resolveFileNodeSuspend(
        projectId: String,
        repoName: String,
        fullPath: String,
        findCurrentNode: suspend (parent: Long, name: String) -> TDriveNode?,
    ): TDriveNode? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        val parentPath = PathUtils.resolveParent(normalizedPath)
        val fileName = PathUtils.resolveName(normalizedPath)
        val parentIno = resolveParentInoSuspend(projectId, repoName, parentPath, findCurrentNode) ?: return null
        val node = findCurrentNode(parentIno, fileName) ?: return null
        return if (node.type == TYPE_FILE) node else null
    }

    fun resolveParentIno(
        projectId: String,
        repoName: String,
        parentPath: String,
        findCurrentNode: (parent: Long, name: String) -> TDriveNode?,
    ): Long? {
        if (PathUtils.isRoot(parentPath)) {
            return ROOT_INO
        }
        val segments = parentPath.removePrefix(PathUtils.ROOT)
            .split(PathUtils.UNIX_SEPARATOR)
            .filter { it.isNotBlank() }
        var parentIno = ROOT_INO
        for (segment in segments) {
            val existing = findCurrentNode(parentIno, segment) ?: return null
            if (existing.type != TYPE_DIRECTORY) {
                return null
            }
            parentIno = existing.ino
        }
        return parentIno
    }

    suspend fun resolveParentInoSuspend(
        projectId: String,
        repoName: String,
        parentPath: String,
        findCurrentNode: suspend (parent: Long, name: String) -> TDriveNode?,
    ): Long? {
        if (PathUtils.isRoot(parentPath)) {
            return ROOT_INO
        }
        val segments = parentPath.removePrefix(PathUtils.ROOT)
            .split(PathUtils.UNIX_SEPARATOR)
            .filter { it.isNotBlank() }
        var parentIno = ROOT_INO
        for (segment in segments) {
            val existing = findCurrentNode(parentIno, segment) ?: return null
            if (existing.type != TYPE_DIRECTORY) {
                return null
            }
            parentIno = existing.ino
        }
        return parentIno
    }
}
