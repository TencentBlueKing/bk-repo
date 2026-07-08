package com.tencent.bkrepo.common.metadata.service.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode

interface DriveNodeService {

    fun resolveFileNode(projectId: String, repoName: String, fullPath: String): TDriveNode?
}
