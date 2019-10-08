package com.tencent.bkrepo.auth.service.inner

import com.tencent.bkrepo.auth.pojo.PermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import org.slf4j.LoggerFactory

object AuthUtil {
    private val logger = LoggerFactory.getLogger(AuthUtil::class.java)

    fun checkAuth(permissionRequest: PermissionRequest, permissions: List<Permission>): Boolean {
        permissions.forEach { permission ->
            if (check(permissionRequest, permission)) {
                return true
            }
        }
        return false
    }

    private fun check(request: PermissionRequest, permission: Permission): Boolean {
        when (request.resourceType) {
            ResourceType.PROJECT -> { // 项目管理权限，项目权限匹配 -> 通过
                if (permission.resourceType == ResourceType.PROJECT) {
                    return permission.action == PermissionAction.MANAGE || permission.action == request.action
                }
                return false
            }
            ResourceType.REPO, ResourceType.NODE  -> { // 项目管理权限，项目权限匹配，仓库权限匹配 -> 通过
                if (permission.resourceType == ResourceType.PROJECT) {
                    return permission.action == PermissionAction.MANAGE || permission.action == request.action
                }
                if (permission.resourceType == ResourceType.REPO) {
                    return permission.action == request.action
                        && (permission.repoId == "*" || permission.repoId == request.repoId)
                }
                return false
            }
            else -> {
                throw RuntimeException("unsupported resource type")
            }
        }
    }


}