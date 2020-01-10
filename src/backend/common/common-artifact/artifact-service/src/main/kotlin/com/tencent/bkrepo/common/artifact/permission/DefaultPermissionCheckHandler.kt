package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
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

    override fun onPermissionCheck(userId: String, permission: Permission, artifactInfo: ArtifactInfo, repositoryInfo: RepositoryInfo) {
        if (permission.type == ResourceType.REPO && permission.action == PermissionAction.READ && repositoryInfo.public) {
            // public仓库且为READ操作，直接跳过
            return
        }
        // } else if (userId == ANONYMOUS_USER) {
        //     throw ClientAuthException("Authentication required")
        // }
        val checkRequest = CheckPermissionRequest(userId, permission.type, permission.action, artifactInfo.projectId, artifactInfo.repoName)
        if (!permissionService.hasPermission(checkRequest)) {
            throw PermissionCheckException("Access Forbidden")
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
