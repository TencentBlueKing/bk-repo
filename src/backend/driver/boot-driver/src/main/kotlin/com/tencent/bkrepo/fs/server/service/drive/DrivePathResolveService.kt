package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import org.springframework.stereotype.Service

@Service
class DrivePathResolveService(
    private val driveNodeDao: RDriveNodeDao,
) {
    suspend fun resolveFileNode(
        projectId: String,
        repoName: String,
        fullPath: String,
        snapSeq: Long? = null,
    ): TDriveNode? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        val fileName = PathUtils.resolveName(normalizedPath)
        if (fileName.isBlank()) {
            return null
        }
        val parentIno = resolveParentIno(projectId, repoName, normalizedPath, snapSeq) ?: return null
        return driveNodeDao.findSnapshotNode(projectId, repoName, parentIno, fileName, snapSeq)
            ?.takeIf { it.type == TYPE_FILE }
    }

    private suspend fun resolveParentIno(
        projectId: String,
        repoName: String,
        fullPath: String,
        snapSeq: Long?,
    ): Long? {
        val parentPath = PathUtils.resolveParent(fullPath)
        if (PathUtils.isRoot(parentPath)) {
            return DriveNodeQueryHelper.ROOT_INO
        }
        val segments = parentPath.removePrefix(PathUtils.ROOT)
            .split(PathUtils.UNIX_SEPARATOR)
            .filter { it.isNotBlank() }
        var parentIno = DriveNodeQueryHelper.ROOT_INO
        for (segment in segments) {
            val existing = driveNodeDao.findSnapshotNode(
                projectId,
                repoName,
                parentIno,
                segment,
                snapSeq,
            ) ?: return null
            if (existing.type == TYPE_DIRECTORY) {
                parentIno = existing.ino
            } else {
                return null
            }
        }
        return parentIno
    }
}
