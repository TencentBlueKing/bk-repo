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
        val parentIno = resolveDirectoryIno(projectId, repoName, PathUtils.resolveParent(normalizedPath), snapSeq)
            ?: return null
        return driveNodeDao.findSnapshotNode(projectId, repoName, parentIno, fileName, snapSeq)
            ?.takeIf { it.type == TYPE_FILE }
    }

    /**
     * 按 ino 解析普通文件的 fullPath 与节点；节点不存在或非文件时返回 null。
     */
    suspend fun resolveFilePathInfoByIno(
        projectId: String,
        repoName: String,
        ino: Long,
        snapSeq: Long? = null,
    ): DriveFilePathInfo? {
        val node = driveNodeDao.findByProjectIdAndRepoNameAndIno(projectId, repoName, ino, snapSeq)
            ?.takeIf { it.type == TYPE_FILE }
            ?: return null
        val segments = mutableListOf(node.name)
        var parentIno = node.parent
        while (parentIno != null && parentIno != DriveNodeQueryHelper.ROOT_INO) {
            val parent = driveNodeDao.findByProjectIdAndRepoNameAndIno(projectId, repoName, parentIno, snapSeq)
                ?: return null
            segments.add(0, parent.name)
            parentIno = parent.parent
        }
        val fullPath = PathUtils.ROOT + segments.joinToString(PathUtils.UNIX_SEPARATOR.toString())
        return DriveFilePathInfo(fullPath = fullPath, node = node)
    }

    /**
     * 将 fullPath 解析为目录 inode。根路径返回 [DriveNodeQueryHelper.ROOT_INO]。
     * 路径不存在或指向非目录时返回 null。
     */
    suspend fun resolveDirectoryIno(
        projectId: String,
        repoName: String,
        fullPath: String,
        snapSeq: Long? = null,
    ): Long? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        if (PathUtils.isRoot(normalizedPath)) {
            return DriveNodeQueryHelper.ROOT_INO
        }
        val segments = normalizedPath.removePrefix(PathUtils.ROOT)
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
            if (existing.type != TYPE_DIRECTORY) {
                return null
            }
            parentIno = existing.ino
        }
        return parentIno
    }
}
