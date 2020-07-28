package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class DefaultPermissionCheckHandler : PermissionCheckHandler {

    @Autowired
    private lateinit var permissionService: PermissionService

    override fun onPermissionCheck(userId: String, permission: Permission, repositoryInfo: RepositoryInfo, artifactInfo: ArtifactInfo?) {
        permissionService.checkPermission(userId, permission.type, permission.action, repositoryInfo)
    }

    override fun onPrincipalCheck(userId: String, principal: Principal) {
        permissionService.checkPrincipal(userId, principal.type)
    }

    override fun onPermissionCheckFailed(exception: PermissionCheckException) {
        // 默认向上抛异常，由ArtifactExceptionHandler统一处理
        throw exception
    }

    override fun onPermissionCheckSuccess() {
        // do nothing
    }
}
