package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 权限服务类
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Component
class PermissionService @Autowired constructor(
    private val repositoryResource: RepositoryResource,
    private val servicePermissionResource: ServicePermissionResource,
    private val authProperties: AuthProperties
) {

    fun checkPermission(request: CheckPermissionRequest) {
        if (preCheck()) return
        // auth 校验
        if (servicePermissionResource.checkPermission(request).data != true) {
            throw PermissionCheckException("Access Forbidden")
        }
    }

    fun checkPermission(userId: String, type: ResourceType, action: PermissionAction, repositoryInfo: RepositoryInfo) {
        if (preCheck()) return
        // 判断是否拥有权限
        if (!hasPermission(userId, type, action, repositoryInfo)) {
            throw PermissionCheckException("Access Forbidden")
        }
    }

    fun checkPermission(userId: String, type: ResourceType, action: PermissionAction, projectId: String, repoName: String) {
        if (preCheck()) return
        // 查询repository信息
        val repositoryInfo = queryRepositoryInfo(projectId, repoName)
        // 判断是否拥有权限
        if (!hasPermission(userId, type, action, repositoryInfo)) {
            throw PermissionCheckException("Access Forbidden")
        }
    }

    private fun preCheck(): Boolean {
        return if (!authProperties.enabled) true else HttpContextHolder.getRequest().getAttribute(APP_KEY) != null
    }

    private fun queryRepositoryInfo(projectId: String, repoName: String): RepositoryInfo {
        val response = repositoryResource.detail(projectId, repoName)
        return response.data ?: throw ArtifactNotFoundException("Repository[$repoName] not found")
    }

    private fun hasPermission(userId: String, type: ResourceType, action: PermissionAction, repositoryInfo: RepositoryInfo): Boolean {
        // public仓库且为READ操作，直接跳过
        if (type == ResourceType.REPO && action == PermissionAction.READ && repositoryInfo.public) return true
        // 匿名用户，提示登录
        if (userId == ANONYMOUS_USER) throw ClientAuthException("Authentication required")
        // auth 校验
        with(repositoryInfo) {
            val checkRequest = CheckPermissionRequest(userId, type, action, projectId, name)
            return servicePermissionResource.checkPermission(checkRequest).data == true
        }
    }
}
