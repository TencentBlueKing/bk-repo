package com.tencent.bkrepo.common.metadata.service.drive.impl

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.drive.DriveNodeDao
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.service.drive.DriveNodeService
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodePathHelper
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(SyncCondition::class)
class DriveNodeServiceImpl(
    private val driveNodeDao: DriveNodeDao,
) : DriveNodeService {

    override fun resolveFileNode(projectId: String, repoName: String, fullPath: String): TDriveNode? {
        return DriveNodePathHelper.resolveFileNode(projectId, repoName, fullPath) { parent, name ->
            driveNodeDao.findCurrentNode(projectId, repoName, parent, name)
        }
    }
}
