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

package com.tencent.bkrepo.common.security.manager

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.RegisterResourceRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory

/**
 * 权限管理类
 */
class PermissionManager(
    private val repositoryClient: RepositoryClient,
    private val permissionResource: ServicePermissionResource,
    private val userResource: ServiceUserResource,
    private val httpAuthProperties: HttpAuthProperties
) {

    fun checkPermission(
        userId: String,
        type: ResourceType,
        action: PermissionAction,
        projectId: String,
        repoName: String? = null,
        repoPublic: Boolean? = null
    ) {
        when {
            preCheck() -> return
            type == ResourceType.PROJECT -> checkProjectPermission(userId, type, action, projectId)
            repoName != null && repoPublic != null -> {
                checkRepoPermission(userId, type, action, projectId, repoName, repoPublic)
            }
            else -> {
                require(repoName != null) { "Repo name must not be null!" }
                val repoInfo = queryRepositoryInfo(projectId, repoName)
                checkRepoPermission(userId, type, action, repoInfo.projectId, repoInfo.name, repoInfo.public)
            }
        }
    }

    fun checkPrincipal(userId: String, principalType: PrincipalType) {
        if (preCheck()) {
            return
        }
        // 匿名用户，提示登录
        val platformId = HttpContextHolder.getRequest().getAttribute(PLATFORM_KEY) as? String
        if (userId == ANONYMOUS_USER && platformId == null) throw AuthenticationException()

        if (principalType == PrincipalType.ADMIN) {
            if (!isAdminUser(userId)) {
                throw PermissionException()
            }
        } else if (principalType == PrincipalType.PLATFORM) {
            if (!isPlatformUser() && !isAdminUser(userId)) {
                throw PermissionException()
            }
        }
    }

    fun registerProject(userId: String, projectId: String) {
        permissionResource.registerResource(RegisterResourceRequest(userId, ResourceType.PROJECT, projectId))
    }

    fun registerRepo(userId: String, projectId: String, repoName: String) {
        permissionResource.registerResource(RegisterResourceRequest(userId, ResourceType.PROJECT, projectId, repoName))
    }

    private fun preCheck(): Boolean {
        return if (!httpAuthProperties.enabled) {
            if (logger.isDebugEnabled) {
                logger.debug("Auth disabled, skip checking permission")
            }
            true
        } else false
    }

    private fun queryRepositoryInfo(projectId: String, repoName: String): RepositoryInfo {
        return repositoryClient.getRepoInfo(projectId, repoName).data
            ?: throw ArtifactNotFoundException("Repository[$repoName] not found")
    }

    private fun checkRepoPermission(
        userId: String,
        type: ResourceType,
        action: PermissionAction,
        projectId: String,
        repoName: String,
        repoPublic: Boolean
    ) {
        // public仓库且为READ操作，直接跳过
        if (type == ResourceType.REPO && action == PermissionAction.READ && repoPublic) return

        // 匿名用户，提示登录
        val appId = HttpContextHolder.getRequest().getAttribute(PLATFORM_KEY) as? String
        if (userId == ANONYMOUS_USER && appId == null) throw AuthenticationException()

        // auth 校验
        val checkRequest = CheckPermissionRequest(
            uid = userId,
            appId = appId,
            resourceType = type,
            action = action,
            projectId = projectId,
            repoName = repoName
        )
        checkPermission(checkRequest)
    }

    private fun checkProjectPermission(
        userId: String,
        type: ResourceType,
        action: PermissionAction,
        projectId: String
    ) {
        // 匿名用户，提示登录
        val platformId = HttpContextHolder.getRequest().getAttribute(PLATFORM_KEY) as? String
        if (userId == ANONYMOUS_USER && platformId == null) throw AuthenticationException()

        val checkRequest = CheckPermissionRequest(
            uid = userId,
            appId = platformId,
            resourceType = type,
            action = action,
            projectId = projectId
        )
        checkPermission(checkRequest)
    }

    private fun checkPermission(checkRequest: CheckPermissionRequest) {
        if (permissionResource.checkPermission(checkRequest).data != true) {
            throw PermissionException()
        }
    }

    private fun isPlatformUser(): Boolean {
        return HttpContextHolder.getRequest().getAttribute(PLATFORM_KEY) != null
    }

    private fun isAdminUser(userId: String): Boolean {
        return userResource.detail(userId).data?.admin == true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionManager::class.java)
    }
}
