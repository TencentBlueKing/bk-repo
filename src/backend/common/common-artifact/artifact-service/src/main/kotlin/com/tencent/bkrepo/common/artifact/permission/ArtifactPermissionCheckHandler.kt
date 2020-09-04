package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.PermissionCheckHandler
import com.tencent.bkrepo.common.security.permission.Principal
import org.springframework.stereotype.Component

@Component
class ArtifactPermissionCheckHandler(
    private val permissionManager: PermissionManager
) : PermissionCheckHandler {
    override fun onPermissionCheck(userId: String, permission: Permission) {
        val repositoryDetail = ArtifactContextHolder.getRepoDetail()!!
        with(repositoryDetail) {
            permissionManager.checkPermission(userId, permission.type, permission.action, projectId, name, public)
        }
    }

    override fun onPrincipalCheck(userId: String, principal: Principal) {
        permissionManager.checkPrincipal(userId, principal.type)
    }
}
