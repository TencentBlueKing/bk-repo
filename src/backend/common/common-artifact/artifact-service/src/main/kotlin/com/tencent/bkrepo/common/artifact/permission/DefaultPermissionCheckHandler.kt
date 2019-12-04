package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.auth.PermissionService
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
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
            try {
                permissionService.checkPermission(CheckPermissionRequest(userId, permission.type, permission.action, projectId, repoName))
            } catch (exception: ErrorCodeException) {
                throw PermissionCheckException(exception)
            }
        }
    }

    override fun onPermissionCheckFailed(request: HttpServletRequest, response: HttpServletResponse) {
        response.status = SC_FORBIDDEN
    }

    override fun onPermissionCheckSuccess(request: HttpServletRequest, response: HttpServletResponse) {
        // do nothing
    }
}
