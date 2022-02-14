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

import com.tencent.bkrepo.auth.api.ServiceExternalPermissionResource
import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.RegisterResourceRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.externalPermission.ExternalPermission
import com.tencent.bkrepo.auth.pojo.externalPermission.Rule
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 权限管理类
 */
open class PermissionManager(
    private val repositoryClient: RepositoryClient,
    private val permissionResource: ServicePermissionResource,
    private val externalPermissionResource: ServiceExternalPermissionResource,
    private val userResource: ServiceUserResource,
    private val httpAuthProperties: HttpAuthProperties,
    private val nodeClient: NodeClient
) {

    @Value("\${service.name:}")
    private var applicationName: String = ""

    private val externalPermissionList by lazy {
        externalPermissionResource.listExternalPermission().data!!
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5L, TimeUnit.SECONDS)
        .readTimeout(10L, TimeUnit.SECONDS)
        .writeTimeout(10L, TimeUnit.SECONDS)
        .build()

    /**
     * 校验项目权限
     * @param action 动作
     * @param projectId 项目id
     */
    open fun checkProjectPermission(
        action: PermissionAction,
        projectId: String
    ) {
        checkPermission(ResourceType.PROJECT, action, projectId)
    }

    /**
     * 校验仓库权限
     * @param action 动作
     * @param projectId 项目id
     * @param repoName 仓库名称
     * @param public 仓库是否为public
     * @param anonymous 是否允许匿名
     */
    open fun checkRepoPermission(
        action: PermissionAction,
        projectId: String,
        repoName: String,
        public: Boolean? = null,
        anonymous: Boolean = false
    ) {
        if (isReadPublicRepo(action, projectId, repoName, public)) {
            return
        }
        checkPermission(
            type = ResourceType.REPO,
            action = action,
            projectId = projectId,
            repoName = repoName,
            anonymous = anonymous
        )
    }

    /**
     * 校验节点权限
     * @param action 动作
     * @param projectId 项目id
     * @param repoName 仓库名称
     * @param path 节点路径
     * @param public 仓库是否为public
     * @param anonymous 是否允许匿名
     */
    open fun checkNodePermission(
        action: PermissionAction,
        projectId: String,
        repoName: String,
        path: String,
        public: Boolean? = null,
        anonymous: Boolean = false
    ) {
        if (isReadPublicRepo(action, projectId, repoName, public)) {
            return
        }
        checkPermission(
            type = ResourceType.NODE,
            action = action,
            projectId = projectId,
            repoName = repoName,
            path = path,
            anonymous = anonymous
        )
    }

    /**
     * 校验身份
     * @param userId 用户id
     * @param principalType 身份类型
     */
    fun checkPrincipal(userId: String, principalType: PrincipalType) {
        if (!httpAuthProperties.enabled) {
            return
        }
        val platformId = SecurityUtils.getPlatformId()
        checkAnonymous(userId, platformId)

        if (principalType == PrincipalType.ADMIN) {
            if (!isAdminUser(userId)) {
                throw PermissionException()
            }
        } else if (principalType == PrincipalType.PLATFORM) {
            if (platformId == null && !isAdminUser(userId)) {
                throw PermissionException()
            }
        }
    }

    fun registerProject(userId: String, projectId: String) {
        val request = RegisterResourceRequest(userId, ResourceType.PROJECT.toString(), projectId)
        permissionResource.registerResource(request)
    }

    fun registerRepo(userId: String, projectId: String, repoName: String) {
        val request = RegisterResourceRequest(userId, ResourceType.REPO.toString(), projectId, repoName)
        permissionResource.registerResource(request)
    }

    /**
     * 判断是否为public仓库且为READ操作
     */
    private fun isReadPublicRepo(
        action: PermissionAction,
        projectId: String,
        repoName: String,
        public: Boolean? = null
    ): Boolean {
        if (action != PermissionAction.READ) {
            return false
        }
        return public ?: queryRepositoryInfo(projectId, repoName).public
    }

    /**
     * 查询仓库信息
     */
    private fun queryRepositoryInfo(projectId: String, repoName: String): RepositoryInfo {
        return repositoryClient.getRepoInfo(projectId, repoName).data ?: throw RepoNotFoundException(repoName)
    }

    /**
     * 去auth微服务校验资源权限
     */
    private fun checkPermission(
        type: ResourceType,
        action: PermissionAction,
        projectId: String,
        repoName: String? = null,
        path: String? = null,
        anonymous: Boolean = false
    ) {
        // 判断是否开启认证
        if (!httpAuthProperties.enabled) {
            return
        }
        val userId = SecurityUtils.getUserId()
        val platformId = SecurityUtils.getPlatformId()
        checkAnonymous(userId, platformId)

        if (userId == ANONYMOUS_USER && platformId != null && anonymous) {
            return
        }

        // 校验Oauth token对应权限
        val authorities = SecurityUtils.getAuthorities()
        if (authorities.isNotEmpty() && !authorities.contains(type.toString())) {
            throw PermissionException()
        }

        // 自定义外部权限校验
        val externalPermission = getExternalPermission(projectId, repoName)
        if (externalPermission != null) {
            checkExternalPermission(externalPermission, userId, type, action, projectId, repoName, path)
            return
        }

        // 去auth微服务校验资源权限
        val checkRequest = CheckPermissionRequest(
            uid = userId,
            appId = platformId,
            resourceType = type.toString(),
            action = action.toString(),
            projectId = projectId,
            repoName = repoName,
            path = path
        )
        if (permissionResource.checkPermission(checkRequest).data != true) {
            // 无权限，响应403错误
            var reason = "user[$userId] does not have $action permission in project[$projectId]"
            repoName?.let { reason += " repo[$repoName]" }
            throw PermissionException(reason)
        }
        if (logger.isDebugEnabled) {
            logger.debug("User[${SecurityUtils.getPrincipal()}] check permission success.")
        }
    }

    /**
     * 获取当前项目、仓库的自定义外部权限
     */
    private fun getExternalPermission(projectId: String, repoName: String?): ExternalPermission? {
        val ext = externalPermissionList.firstOrNull { p ->
            p.enabled
                .and(p.scope.contains(applicationName))
//                .and(apiName.matches(Regex(p.scope.replace("*", ".*"))))
                .and(projectId.matches(Regex(p.projectId.replace("*", ".*"))))
                .and(repoName?.matches(Regex(p.repoName.replace("*", ".*"))) ?: true)
        }
        return ext
    }

    /**
     * 检查外部权限
     */
    private fun checkExternalPermission(
        externalPermission: ExternalPermission,
        userId: String,
        type: ResourceType,
        action: PermissionAction,
        projectId: String,
        repoName: String?,
        path: String?
    ) {
        var errorMsg = "user[$userId] does not have $action permission in project[$projectId] repo[$repoName]"
        path?.let { errorMsg = errorMsg.plus(" path[$path]") }
        if (!checkRules(externalPermission.rules, projectId, repoName, path)) {
            throw PermissionException(errorMsg)
        }
        val url = externalPermission.url
        val node: NodeDetail? = if (repoName.isNullOrBlank() || path.isNullOrBlank()) {
            null
        } else {
            nodeClient.getNodeDetail(projectId, repoName, path).data ?: throw NodeNotFoundException(path)
        }
        val headersBuilder = Headers.Builder()
        externalPermission.headers?.forEach { (k, v) ->
            headersBuilder[k] = v
        }
        val requestData = mutableMapOf<String, Any>()
        requestData[USER_ID] = userId
        requestData[TYPE] = type.toString()
        requestData[ACTION] = action.toString()
        requestData[PROJECT_ID] = projectId
        repoName?.let { requestData[REPO_NAME] = repoName }
        path?.let { requestData[PATH] = path }
        node?.let {
            requestData[FULL_PATH] = it.fullPath
            requestData[METADATA] = it.metadata
        }
        val requestBody = RequestBody.create(MediaType.parse(MediaTypes.APPLICATION_JSON), requestData.toJsonString())
        val request = Request.Builder().url(url).headers(headersBuilder.build()).post(requestBody).build()

        try {
            httpClient.newCall(request).execute().use {
                if (it.isSuccessful) {
                    return
                }

                if (it.code() != HttpStatus.FORBIDDEN.value) {
                    logger.warn(
                        "check external permission error, url[$url], code[${it.code()}]"
                    )
                }
                throw PermissionException(errorMsg)
            }
        } catch (e: IOException) {
            logger.error("check external permission error, url[$url], $e")
            throw PermissionException(errorMsg)
        }
    }

    private fun checkRules(rules: List<Rule>?, projectId: String, repoName: String?, path: String?): Boolean {
        if (rules.isNullOrEmpty()) {
            return true
        }

        rules.forEach {
             val pass = when(it.paramName) {
                PROJECT_ID -> Rule.checkProjectId(it, projectId)
                REPO_NAME -> Rule.checkRepoName(it, repoName)
                PATH -> Rule.checkPath(it, path)
                else -> true
            }
            if (!pass) {
                return false
            }
        }

        return true
    }

    /**
     * 判断是否为管理员
     */
    private fun isAdminUser(userId: String): Boolean {
        return userResource.detail(userId).data?.admin == true
    }

    fun enableAuth(): Boolean {
        return httpAuthProperties.enabled
    }

    companion object {

        private val logger = LoggerFactory.getLogger(PermissionManager::class.java)
        private const val USER_ID = "userId"
        private const val TYPE = "type"
        private const val ACTION = "action"
        private const val PROJECT_ID = "project_id"
        private const val REPO_NAME = "repoName"
        private const val PATH = "path"
        private const val FULL_PATH = "fullPath"
        private const val METADATA = "metadata"

        /**
         * 检查是否为匿名用户，如果是匿名用户则返回401并提示登录
         */
        private fun checkAnonymous(userId: String, platformId: String?) {
            if (userId == ANONYMOUS_USER && platformId == null) {
                throw AuthenticationException()
            }
        }
    }
}
