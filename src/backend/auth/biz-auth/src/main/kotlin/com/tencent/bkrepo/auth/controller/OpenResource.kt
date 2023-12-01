/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory

open class OpenResource(private val permissionService: PermissionService) {

    /**
     * the userContext should equal userId or be admin
     */
    fun preCheckContextUser(userId: String) {
        val userContext = SecurityUtils.getUserId()
        if (!SecurityUtils.isAdmin() && userContext.isNotEmpty() && userContext != userId) {
            logger.warn("user not match [$userContext, $userId]")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    /**
     *  userId's assetUsers contain userContext or userContext be admin
     */
    fun preCheckUserOrAssetUser(userId: String, users: List<UserInfo>) {
        if (!users.any { userInfo -> userInfo.userId.equals(userId) }) {
            preCheckContextUser(userId)
        }
    }

    /**
     * the userContext should be admin
     */
    fun preCheckUserAdmin() {
        val userContext = SecurityUtils.getUserId()
        if (!SecurityUtils.isAdmin()) {
            logger.warn("user not match admin [$userContext]")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    /**
     * only system scopeType account have the permission
     */
    fun preCheckPlatformPermission() {
        val appId = SecurityUtils.getPlatformId()
        if (appId.isNullOrEmpty()) {
            logger.warn("appId can not be empty [$appId]")
            throw ErrorCodeException(AuthMessageCode.AUTH_ACCOUT_FORAUTH_NOT_PERM)
        }
        val request = CheckPermissionRequest(
            uid = SecurityUtils.getUserId(),
            appId = appId,
            resourceType = ResourceType.SYSTEM.name,
            action = PermissionAction.MANAGE.name
        )

        if (!permissionService.checkPlatformPermission(request)) {
            logger.warn("account do not have the permission [$request]")
            throw ErrorCodeException(AuthMessageCode.AUTH_ACCOUT_FORAUTH_NOT_PERM)
        }
    }

    /**
     * check is the user have project or repo create permission
     */
    fun preCheckUserInProject(type: AuthPermissionType, projectId: String, repoName: String?) {
        val checkRequest = CheckPermissionRequest(
            uid = SecurityUtils.getUserId(),
            resourceType = ResourceType.PROJECT.toString(),
            action = PermissionAction.WRITE.toString(),
            projectId = projectId,
            appId = SecurityUtils.getPlatformId()
        )
        if (type == AuthPermissionType.REPO) {
            checkRequest.repoName = repoName
            checkRequest.resourceType = ResourceType.REPO.toString()
        }
        if (!permissionService.checkPermission(checkRequest)) {
            logger.warn("check user permission error [$checkRequest]")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_FAILED)
        }
    }

    /**
     * check is the user is project admin
     */
    fun preCheckProjectAdmin(projectId: String) {
        val userId = SecurityUtils.getUserId()
        val checkRequest = CheckPermissionRequest(
            uid = userId,
            resourceType = ResourceType.PROJECT.toString(),
            projectId = projectId,
            action = PermissionAction.MANAGE.toString()
        )
        if (!permissionService.checkPermission(checkRequest)) {
            logger.warn("user is not project admin [$checkRequest]")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    fun isContextUserProjectAdmin(projectId: String): Boolean {
        val userId = SecurityUtils.getUserId()
        val checkRequest = CheckPermissionRequest(
            uid = userId,
            resourceType = ResourceType.PROJECT.toString(),
            projectId = projectId,
            action = PermissionAction.MANAGE.toString()
        )
        return permissionService.checkPermission(checkRequest)
    }

    fun checkRequest(request: CheckPermissionRequest) {
        with(request) {
            when (resourceType) {
                ResourceType.PROJECT.toString() -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                }
                ResourceType.REPO.toString() -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                    if (repoName.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "repoName")
                    }
                }
                ResourceType.NODE.toString() -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                    if (repoName.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "repoName")
                    }
                    if (path.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "path")
                    }
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OpenResource::class.java)
    }
}
