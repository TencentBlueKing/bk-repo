package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.artifact.auth.AuthProperties
import com.tencent.bkrepo.common.artifact.config.PERMISSION_PROMPT
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 权限服务类
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Component
class PermissionService {
    @Autowired
    private lateinit var repositoryResource: RepositoryResource
    @Autowired
    private lateinit var permissionResource: ServicePermissionResource
    @Autowired
    private lateinit var userResource: ServiceUserResource
    @Autowired
    private lateinit var authProperties: AuthProperties

    fun checkPermission(userId: String, type: ResourceType, action: PermissionAction, repositoryInfo: RepositoryInfo) {
        if (preCheck()) return
        checkRepoPermission(userId, type, action, repositoryInfo)
    }

    fun checkPermission(userId: String, type: ResourceType, action: PermissionAction, projectId: String, repoName: String? = null) {
        if (preCheck()) return
        if (type == ResourceType.PROJECT) {
            checkProjectPermission(userId, type, action, projectId)
        } else {
            val repositoryInfo = queryRepositoryInfo(projectId, repoName!!)
            checkRepoPermission(userId, type, action, repositoryInfo)
        }
    }

    fun checkPrincipal(userId: String, principalType: PrincipalType) {
        if (!authProperties.enabled) {
            logger.debug("Auth disabled, skip checking principal")
            return
        }
        // 匿名用户，提示登录
        if (userId == ANONYMOUS_USER) throw ClientAuthException()
        if (principalType == PrincipalType.ADMIN) {
            if (!isAdminUser(userId)) {
                throw PermissionCheckException(PERMISSION_PROMPT)
            }
        } else if (principalType == PrincipalType.PLATFORM) {
            if (!isPlatformUser() && !isAdminUser(userId)) {
                throw PermissionCheckException(PERMISSION_PROMPT)
            }
        }
    }

    private fun preCheck(): Boolean {
        return if (!authProperties.enabled) {
            logger.debug("Auth disabled, skip checking permission")
            true
        } else {
            false
        }
    }

    private fun queryRepositoryInfo(projectId: String, repoName: String): RepositoryInfo {
        val response = repositoryResource.detail(projectId, repoName)
        return response.data ?: throw ArtifactNotFoundException("Repository[$repoName] not found")
    }

    private fun checkRepoPermission(userId: String, type: ResourceType, action: PermissionAction, repositoryInfo: RepositoryInfo) {
        // public仓库且为READ操作，直接跳过
        if (type == ResourceType.REPO && action == PermissionAction.READ && repositoryInfo.public) return
        val appId = HttpContextHolder.getRequest().getAttribute(APP_KEY) as? String
        // 匿名用户，提示登录
        if (userId == ANONYMOUS_USER && appId == null) throw ClientAuthException()
        // auth 校验
        with(repositoryInfo) {
            val checkRequest = CheckPermissionRequest(userId, type, action, projectId, name, null, null, appId)
            checkPermission(checkRequest)
        }
    }

    private fun checkProjectPermission(userId: String, type: ResourceType, action: PermissionAction, projectId: String) {
        val checkRequest = CheckPermissionRequest(userId, type, action, projectId)
        checkPermission(checkRequest)
    }

    private fun checkPermission(checkRequest: CheckPermissionRequest) {
        if (permissionResource.checkPermission(checkRequest).data != true) {
            throw PermissionCheckException("Access Forbidden")
        }
    }

    private fun isPlatformUser(): Boolean {
        return HttpContextHolder.getRequest().getAttribute(APP_KEY) != null
    }

    private fun isAdminUser(userId: String): Boolean {
        return userResource.detail(userId).data?.admin == true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionService::class.java)
    }
}
