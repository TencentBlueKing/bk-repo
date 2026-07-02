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
