/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.auth.controller

import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.enums.AuthPermissionType
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.common.api.constant.ADMIN_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.slf4j.LoggerFactory

open class OpenResource(private val permissionService: PermissionService) {

    /**
     * 检查当前用户上下文是否与指定用户ID匹配或为管理员
     * 仅用于用户API
     */
    fun preCheckContextUser(userId: String) {
        val userContext = SecurityUtils.getUserId()
        if (!isAdminFromApi() && userContext.isNotEmpty() && userContext != userId) {
            logger.warn("User context mismatch: expected [$userId], actual [$userContext]")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    /**
     * 检查是否为系统管理员
     * 限定在auth服务API请求时使用
     */
    private fun isAdminFromApi(): Boolean {
        return HttpContextHolder.getRequestOrNull()?.getAttribute(ADMIN_USER) as? Boolean ?: false
    }

    /**
     * 检查是否为平台认证
     * 限定在auth服务API请求时使用
     */
    fun isAuthFromPlatform(): Boolean {
        return !SecurityUtils.getPlatformId().isNullOrEmpty()
    }

    /**
     * 检查用户ID的资产用户列表是否包含当前用户上下文，或当前用户为管理员
     */
    fun preCheckUserOrAssetUser(userId: String, users: List<UserInfo>) {
        if (users.none { it.userId == userId }) {
            preCheckContextUser(userId)
        }
    }

    /**
     * 检查当前用户是否为管理员
     * 仅用于用户API
     */
    fun preCheckUserAdmin() {
        if (!isAdminFromApi()) {
            val userContext = SecurityUtils.getUserId()
            logger.warn("User [$userContext] is not admin")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    /**
     * 检查授权类型，如果包含平台授权或为空则需要管理员权限
     * 仅用于用户API
     */
    fun preCheckGrantTypes(grantTypes: Set<AuthorizationGrantType>) {
        if (AuthorizationGrantType.PLATFORM in grantTypes || grantTypes.isEmpty()) {
            preCheckUserAdmin()
        }
    }

    /**
     * 检查平台权限，仅系统级账号拥有此权限
     */
    fun preCheckPlatformPermission() {
        val appId = SecurityUtils.getPlatformId()
        if (appId.isNullOrEmpty()) {
            logger.warn("Platform appId is required but not provided")
            throw ErrorCodeException(AuthMessageCode.AUTH_PLATFORM_ONLY)
        }
        
        val request = buildPermissionRequest(
            resourceType = ResourceType.SYSTEM,
            action = PermissionAction.MANAGE,
            appId = appId
        )

        if (!permissionService.checkPlatformPermission(request)) {
            logger.warn("Account does not have platform permission: $request")
            throw ErrorCodeException(AuthMessageCode.AUTH_ACCOUT_FORAUTH_NOT_PERM)
        }
    }

    /**
     * 检查用户是否拥有项目或仓库的创建权限
     */
    fun preCheckUserInProject(type: AuthPermissionType, projectId: String, repoName: String?) {
        val resourceType = if (type == AuthPermissionType.REPO) ResourceType.REPO else ResourceType.PROJECT
        val checkRequest = buildPermissionRequest(
            resourceType = resourceType,
            action = PermissionAction.WRITE,
            projectId = projectId,
            repoName = repoName.takeIf { type == AuthPermissionType.REPO },
            appId = SecurityUtils.getPlatformId()
        )
        
        if (!permissionService.checkPermission(checkRequest)) {
            logger.warn("User permission check failed: $checkRequest")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_FAILED)
        }
    }

    /**
     * 检查用户是否为项目管理员
     */
    fun preCheckProjectAdmin(projectId: String) {
        if (!isContextUserProjectAdmin(projectId)) {
            val userId = SecurityUtils.getUserId()
            logger.warn("User [$userId] is not project admin for project [$projectId]")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    /**
     * 判断当前用户是否为项目管理员
     */
    fun isContextUserProjectAdmin(projectId: String): Boolean {
        val checkRequest = buildPermissionRequest(
            resourceType = ResourceType.PROJECT,
            action = PermissionAction.MANAGE,
            projectId = projectId
        )
        return permissionService.checkPermission(checkRequest)
    }

    /**
     * 校验权限检查请求参数的完整性
     */
    fun checkRequest(request: CheckPermissionRequest) {
        with(request) {
            when (resourceType) {
                ResourceType.PROJECT.toString() -> {
                    validateParameter(projectId, "projectId")
                }
                ResourceType.REPO.toString() -> {
                    validateParameter(projectId, "projectId")
                    validateParameter(repoName, "repoName")
                }
                ResourceType.NODE.toString() -> {
                    validateParameter(projectId, "projectId")
                    validateParameter(repoName, "repoName")
                    validateParameter(path, "path")
                }
            }
        }
    }

    /**
     * 构建权限检查请求对象
     */
    private fun buildPermissionRequest(
        resourceType: ResourceType,
        action: PermissionAction,
        projectId: String? = null,
        repoName: String? = null,
        path: String? = null,
        appId: String? = null
    ): CheckPermissionRequest {
        return CheckPermissionRequest(
            uid = SecurityUtils.getUserId(),
            resourceType = resourceType.name,
            action = action.name,
            projectId = projectId,
            repoName = repoName,
            path = path,
            appId = appId
        )
    }

    /**
     * 校验参数是否为空
     */
    private fun validateParameter(value: String?, paramName: String) {
        if (value.isNullOrBlank()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, paramName)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OpenResource::class.java)
    }
}