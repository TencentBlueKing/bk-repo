package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.auth.PermissionService
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class DefaultPermissionCheckHandler : PermissionCheckHandler {

    @Autowired
    private lateinit var permissionService: PermissionService

    override fun onPermissionCheck(userId: String, permission: Permission, artifactInfo: ArtifactInfo) {
        artifactInfo.run {
            val checkRequest = CheckPermissionRequest(userId, permission.type, permission.action, projectId, repoName)
            if (permissionService.hasPermission(checkRequest)) {
                throw PermissionCheckException("Access Forbidden")
            }
        }
    }

    override fun onPermissionCheckFailed(exception: PermissionCheckException) {
        // 默认向上抛异常，由ArtifactExceptionHandler统一处理
        throw exception
    }

    override fun onPermissionCheckSuccess() {
        // do nothing
    }
}
