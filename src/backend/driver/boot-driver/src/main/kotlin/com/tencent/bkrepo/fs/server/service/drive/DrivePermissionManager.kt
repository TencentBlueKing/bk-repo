package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.fs.server.service.PermissionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Drive 权限管理
 */
@Component
class DrivePermissionManager(
    private val permissionService: PermissionService,
) {
    suspend fun checkRepoPermission(
        projectId: String,
        repoName: String,
        action: PermissionAction,
        user: String
    ) {
        val hasPermission = permissionService.checkPermission(
            projectId = projectId,
            repoName = repoName,
            action = action,
            uid = user,
        )
        if (!hasPermission) {
            val msg = "user[$user] no [${action.name}] permission in [$projectId/$repoName]"
            logger.info(msg)
            throw PermissionException(msg)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DrivePermissionManager::class.java)
    }
}
