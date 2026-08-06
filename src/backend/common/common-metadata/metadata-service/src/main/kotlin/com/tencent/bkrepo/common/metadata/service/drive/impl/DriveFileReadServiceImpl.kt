package com.tencent.bkrepo.common.metadata.service.drive.impl

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.drive.DriveBlockNodeDao
import com.tencent.bkrepo.common.metadata.service.drive.DriveFileReadService
import com.tencent.bkrepo.common.metadata.service.drive.DriveNodeService
import com.tencent.bkrepo.common.metadata.util.drive.DriveBlockResourceHelper
import com.tencent.bkrepo.fs.server.pojo.DriveFileBlockInfo
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(SyncCondition::class)
class DriveFileReadServiceImpl(
    private val driveNodeService: DriveNodeService,
    private val driveBlockNodeDao: DriveBlockNodeDao,
) : DriveFileReadService {

    override fun getFileBlockInfo(projectId: String, repoName: String, fullPath: String): DriveFileBlockInfo? {
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        val node = driveNodeService.resolveFileNode(projectId, repoName, normalizedPath) ?: return null
        val range = Range.full(node.size)
        val blocks = driveBlockNodeDao.listBlocks(range, projectId, repoName, node.ino)
            .map { DriveBlockResourceHelper.toRegionResource(it) }
        return DriveFileBlockInfo(
            fullPath = normalizedPath,
            fileName = node.name,
            size = node.size,
            blocks = blocks,
        )
    }
}
