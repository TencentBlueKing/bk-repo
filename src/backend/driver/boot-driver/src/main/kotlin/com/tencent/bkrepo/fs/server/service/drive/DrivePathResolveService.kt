package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodePathHelper
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import org.springframework.stereotype.Service

@Service
class DrivePathResolveService(
    private val driveNodeDao: RDriveNodeDao,
) {

    suspend fun resolveFileNode(projectId: String, repoName: String, fullPath: String): TDriveNode? {
        return DriveNodePathHelper.resolveFileNodeSuspend(projectId, repoName, fullPath) { parent, name ->
            driveNodeDao.findCurrentNode(projectId, repoName, parent, name)
        }
    }
}
