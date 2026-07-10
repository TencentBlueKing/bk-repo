package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.util.drive.DriveBlockResourceHelper
import com.tencent.bkrepo.fs.server.pojo.DriveFileBlockInfo
import org.springframework.stereotype.Service

@Service
class DriveFileBlockService(
    private val drivePathResolveService: DrivePathResolveService,
    private val driveBlockNodeService: DriveBlockNodeService,
) {

    suspend fun listFileBlocks(projectId: String, repoName: String, fullPath: String): DriveFileBlockInfo? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        val node = drivePathResolveService.resolveFileNode(projectId, repoName, normalizedPath) ?: return null
        val range = Range.full(node.size)
        val blocks = driveBlockNodeService.listBlocks(
            range = range,
            projectId = projectId,
            repoName = repoName,
            ino = node.ino,
            createdDate = node.createdDate,
        ).map { DriveBlockResourceHelper.toRegionResource(it) }
        return DriveFileBlockInfo(
            fullPath = normalizedPath,
            fileName = node.name,
            size = node.size,
            blocks = blocks,
        )
    }
}
