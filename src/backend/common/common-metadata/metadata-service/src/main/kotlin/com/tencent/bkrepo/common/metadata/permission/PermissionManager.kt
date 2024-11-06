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

package com.tencent.bkrepo.common.metadata.permission

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.externalPermission.ExternalPermission
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.DEVX_ACCESS_FROM_OFFICE
import com.tencent.bkrepo.common.api.constant.HEADER_DEVX_ACCESS_FROM
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.repository.constant.NODE_DETAIL_LIST_KEY
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestContextHolder
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 权限管理类
 */
open class PermissionManager(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val permissionResource: ServicePermissionClient,
    private val externalPermissionResource: ServiceExternalPermissionClient,
    private val userResource: ServiceUserClient,
    private val nodeService: NodeService,
    private val httpAuthProperties: HttpAuthProperties,
    private val principalManager: PrincipalManager
) {

    private val httpClient =
        OkHttpClient.Builder().connectTimeout(10L, TimeUnit.SECONDS).readTimeout(10L, TimeUnit.SECONDS).build()

    private val externalPermissionCache: LoadingCache<String, List<ExternalPermission>> by lazy {
        val cacheLoader = object : CacheLoader<String, List<ExternalPermission>>() {
            override fun load(key: String): List<ExternalPermission> =
                externalPermissionResource.listExternalPermission().data!!
        }
        CacheBuilder.newBuilder().maximumSize(1).expireAfterWrite(30L, TimeUnit.MINUTES).build(cacheLoader)
    }

    /**
     * 校验项目权限
     * @param action 动作
     * @param projectId 项目id
     */
    open fun checkProjectPermission(
        action: PermissionAction,
        projectId: String,
        userId: String = SecurityUtils.getUserId()
    ) {
        projectEnabledCheck(projectId, userId)
        checkPermission(
            type = ResourceType.PROJECT,
            action = action,
            projectId = projectId,
            userId = userId,
        )
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
        anonymous: Boolean = false,
        userId: String = SecurityUtils.getUserId()
    ) {
        if (serviceRequestCheck()) return
        projectEnabledCheck(projectId, userId)
        val repoInfo = queryRepositoryInfo(projectId, repoName)
        if (isReadPublicOrSystemRepoCheck(
                action, repoInfo, public, userId
            )) {
            return
        }
        checkPermission(
            type = ResourceType.REPO,
            action = action,
            projectId = projectId,
            repoName = repoName,
            anonymous = anonymous,
            userId = userId,
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
        vararg path: String,
        public: Boolean? = null,
        anonymous: Boolean = false,
        userId: String = SecurityUtils.getUserId()
    ) {
        if (serviceRequestCheck()) return
        projectEnabledCheck(projectId, userId)
        val repoInfo = queryRepositoryInfo(projectId, repoName)
        if (isReadPublicOrSystemRepoCheck(
                action, repoInfo, public, userId
            )) {
            return
        }
        // 禁止批量下载流水线节点
        if (path.size > 1 && repoName == PIPELINE) {
            throw PermissionException()
        }

        checkPermission(
            type = ResourceType.NODE,
            action = action,
            projectId = projectId,
            repoName = repoName,
            paths = path.toList(),
            anonymous = anonymous,
            userId = userId,
        )
    }

    /**
     * 校验身份
     * @param userId 用户id
     * @param principalType 身份类型
     */
    open fun checkPrincipal(userId: String, principalType: PrincipalType) {
        principalManager.checkPrincipal(userId, principalType)
    }

    /**
     * 判读READ操作是否对应public仓库或者系统级公开仓库
     */
    private fun isReadPublicOrSystemRepoCheck(
        action: PermissionAction,
        repoInfo: RepositoryInfo,
        public: Boolean? = null,
        userId: String = SecurityUtils.getUserId(),
    ): Boolean {
        if (isReadPublicRepo(action, repoInfo, public)) {
            return true
        }
        if (allowReadSystemRepo(action, repoInfo, userId)) {
            return true
        }
        return false
    }


    /**
     * 判断是否为public仓库且为READ或DOWNLOAD操作
     */
    private fun isReadPublicRepo(
        action: PermissionAction,
        repoInfo: RepositoryInfo,
        public: Boolean? = null
    ): Boolean {
        if (action != PermissionAction.READ && action != PermissionAction.DOWNLOAD) {
            return false
        }
        return public ?: repoInfo.public
    }

    /**
     * 判断是否为系统级公开仓库且为READ操作
     */
    @Suppress("TooGenericExceptionCaught")
    private fun allowReadSystemRepo(
        action: PermissionAction,
        repoInfo: RepositoryInfo,
        userId: String = SecurityUtils.getUserId(),
    ): Boolean {
        if (action != PermissionAction.READ && action != PermissionAction.DOWNLOAD) {
            return false
        }
        val platformId = SecurityUtils.getPlatformId()
        checkAnonymous(userId, platformId)
        // 加载仓库信息
        val systemValue = repoInfo.configuration.settings["system"]
        val system = try {
            systemValue as? Boolean
        } catch (e: Exception) {
            logger.error("Repo configuration system field trans failed: $systemValue", e)
            false
        }
        return true == system
    }

    /**
     * 查询项目信息
     */
    open fun queryProjectEnabledStatus(projectId: String): Boolean {
        return try {
            projectService.isProjectEnabled(projectId)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 查询仓库信息
     */
    open fun queryRepositoryInfo(projectId: String, repoName: String): RepositoryInfo {
        return repositoryService.getRepoInfo(projectId, repoName) ?: throw RepoNotFoundException(repoName)
    }

    private fun serviceRequestCheck(): Boolean {
        return SecurityUtils.isServiceRequest()
    }

    private fun projectEnabledCheck(
        projectId: String,
        userId: String = SecurityUtils.getUserId(),
    ) {
        val isAdmin = isAdminUser(userId)
        if (isAdmin) return
        val projectEnabled = queryProjectEnabledStatus(projectId)
        if (projectEnabled) return
        throw PermissionException("Project enabled status is false!")
    }

    /**
     * 去auth微服务校验资源权限
     */
    private fun checkPermission(
        type: ResourceType,
        action: PermissionAction,
        projectId: String,
        repoName: String? = null,
        paths: List<String>? = null,
        anonymous: Boolean = false,
        userId: String = SecurityUtils.getUserId(),
    ) {

        // 判断是否开启认证
        if (!httpAuthProperties.enabled) {
            return
        }
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
            checkExternalPermission(externalPermission, userId, type, action, projectId, repoName, paths)
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
            path = paths?.first(),
        )
        //  devx 是否需要auth 校验仓库维度的访问黑名单
        if (RequestContextHolder.getRequestAttributes() != null) {
            val devxAccessFrom = HttpContextHolder.getRequest().getAttribute(HEADER_DEVX_ACCESS_FROM)
            if (devxAccessFrom == DEVX_ACCESS_FROM_OFFICE) {
                checkRequest.requestSource = DEVX_ACCESS_FROM_OFFICE
            }
        }
        if (checkPermissionFromAuthService(checkRequest) != true) {
            // 无权限，响应403错误
            val reason: String?
            if (repoName.isNullOrEmpty()) {
                val param = arrayOf(userId, action, projectId)
                reason = LocaleMessageUtils.getLocalizedMessage("permission.project.denied", param)
            } else {
                val param = arrayOf(userId, action, projectId, repoName)
                reason = LocaleMessageUtils.getLocalizedMessage("permission.repo.denied", param)
            }
            throw PermissionException(reason)
        }
        if (logger.isDebugEnabled) {
            logger.debug("User[${SecurityUtils.getPrincipal()}] check permission success.")
        }
    }

    open fun checkPermissionFromAuthService(request: CheckPermissionRequest): Boolean? {
        return permissionResource.checkPermission(request).data
    }

    /**
     * 获取当前项目、仓库的自定义外部权限
     */
    open fun getExternalPermission(projectId: String, repoName: String?): ExternalPermission? {
        val externalPermissionList = externalPermissionCache.get(SYSTEM_USER)
        val platformId = SecurityUtils.getPlatformId()
        val ext = externalPermissionList.firstOrNull { p ->
            p.enabled.and(projectId.matches(wildcardToRegex(p.projectId)))
                .and(repoName?.matches(wildcardToRegex(p.repoName)) ?: true).and(matchApi(p.scope))
                .and(p.platformWhiteList.isNullOrEmpty() || !p.platformWhiteList!!.contains(platformId))
        }
        return ext
    }

    /**
     * 匹配需要自定义鉴权的接口
     * 通过straceTrace获取接口名称
     *   1. 过滤包名为com.tencent.bkrepo的接口
     *   2. 使用注解鉴权的接口是由Spring cglib生成的，类名中包含$$EnhancerBySpringCGLIB$$xxxx, 需要替换掉
     *      例如com.tencent.bkrepo.generic.controller.GenericController$$EnhancerBySpringCGLIB$$bccb61f5.download()
     *   3. 去掉括号，得到接口名称
     *      例如com.tencent.bkrepo.generic.controller.GenericController.download
     * 然后scope与接口名称匹配进行正则匹配
     */
    private fun matchApi(scope: String): Boolean {
        val stackTraceElements =
            Thread.currentThread().stackTrace.toList().filter { it.toString().startsWith(PACKAGE_NAME_PREFIX) }.map {
                it.toString().replace(Regex("\\\$\\\$(.*)\\\$\\\$[a-z0-9]+"), "")
                    .substringBefore("(")
            }
        logger.debug("stack trace elements: $stackTraceElements")
        val pattern = wildcardToRegex(scope)
        stackTraceElements.forEach {
            if (pattern.matches(it)) {
                logger.debug("scope[$scope] match api: $it")
                return true
            }
        }
        return false
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
        paths: List<String>?
    ) {
        var errorMsg = "user[$userId] does not have $action permission in project[$projectId] repo[$repoName]"
        paths?.let { errorMsg = errorMsg.plus(" path$paths") }

        val nodes = getNodeDetailList(projectId, repoName, paths)

        val request = buildRequest(externalPermission, type, action, userId, projectId, repoName, nodes)
        callbackToAuth(request, projectId, repoName, paths, errorMsg)
    }

    private fun getNodeDetailList(
        projectId: String,
        repoName: String?,
        paths: List<String>?
    ): List<NodeDetail>? {
        val nodeDetailList = if (repoName.isNullOrBlank() || paths.isNullOrEmpty()) {
            null
        } else if (paths.size == 1) {
            val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, paths.first()))
                ?: throw NodeNotFoundException(paths.first())
            listOf(node)
        } else {
            queryNodeDetailList(projectId, repoName, paths)
        }
        if (!nodeDetailList.isNullOrEmpty()) {
            HttpContextHolder.getRequest().setAttribute(NODE_DETAIL_LIST_KEY, nodeDetailList)
        }
        return nodeDetailList
    }

    private fun queryNodeDetailList(
        projectId: String,
        repoName: String,
        paths: List<String>
    ): List<NodeDetail> {
        val prefix = PathUtils.getCommonParentPath(paths)
        var pageNumber = 1
        val nodeDetailList = mutableListOf<NodeDetail>()
        do {
            val option = NodeListOption(
                pageNumber = pageNumber, pageSize = 1000, includeFolder = true, includeMetadata = true, deep = true
            )
            val records = nodeService.listNodePage(ArtifactInfo(projectId, repoName, prefix), option).records
            if (records.isEmpty()) {
                break
            }
            nodeDetailList.addAll(records.filter { paths.contains(it.fullPath) }.map { NodeDetail(it) })
            pageNumber++
        } while (nodeDetailList.size < paths.size)
        return nodeDetailList
    }

    private fun callbackToAuth(
        request: Request,
        projectId: String,
        repoName: String?,
        paths: List<String>?,
        errorMsg: String
    ) {
        try {
            httpClient.newCall(request).execute().use {
                val content = it.body?.string()
                if (it.isSuccessful && checkResponse(content)) {
                    return
                }
                logger.info(
                    "check external permission error, url[${request.url}], project[$projectId], repo[$repoName]," +
                        " nodes$paths, code[${it.code}], response[$content]"
                )
                throw PermissionException(errorMsg)
            }
        } catch (e: IOException) {
            logger.error(
                "check external permission error," + "url[${request.url}], project[$projectId], " +
                    "repo[$repoName], nodes$paths, $e"
            )
            throw PermissionException(errorMsg)
        }
    }

    private fun checkResponse(content: String?): Boolean {
        if (content.isNullOrBlank()) {
            return true
        }
        logger.debug("response content: $content")
        val data = content.readJsonString<Response<*>>()
        if (data.isNotOk()) {
            return false
        }
        return true
    }

    private fun buildRequest(
        externalPermission: ExternalPermission,
        type: ResourceType,
        action: PermissionAction,
        userId: String,
        projectId: String,
        repoName: String?,
        nodes: List<NodeDetail>?
    ): Request {
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
        nodes?.let {
            val nodeMaps = mutableListOf<Map<String, Any>>()
            it.forEach { nodeDetail ->
                nodeMaps.add(
                    mapOf(
                        FULL_PATH to nodeDetail.fullPath, METADATA to nodeDetail.metadata
                    )
                )
            }
            requestData[NODES] = nodeMaps
        }
        val requestBody = requestData.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        logger.debug("request data: ${requestData.toJsonString()}")
        return Request.Builder().url(externalPermission.url).headers(headersBuilder.build()).post(requestBody).build()
    }

    /**
     * 判断是否为管理员
     */
    open fun isAdminUser(userId: String): Boolean {
        return userResource.userInfoById(userId).data?.admin == true
    }


    companion object {

        private val logger = LoggerFactory.getLogger(PermissionManager::class.java)
        private val keywordList = listOf("\\", "$", "(", ")", "+", ".", "[", "]", "?", "^", "{", "}", "|", "?", "&")

        private const val USER_ID = "userId"
        private const val TYPE = "type"
        private const val ACTION = "action"
        private const val PROJECT_ID = "projectId"
        private const val REPO_NAME = "repoName"
        private const val FULL_PATH = "fullPath"
        private const val METADATA = "metadata"
        private const val NODES = "nodes"
        private const val PACKAGE_NAME_PREFIX = "com.tencent.bkrepo"

        /**
         * 检查是否为匿名用户，如果是匿名用户则返回401并提示登录
         */
        private fun checkAnonymous(userId: String, platformId: String?) {
            if (userId == ANONYMOUS_USER && platformId == null) {
                throw AuthenticationException()
            }
        }

        private fun wildcardToRegex(input: String): Regex {
            var escapedString = input.trim()
            if (escapedString.isNotBlank()) {
                keywordList.forEach {
                    if (escapedString.contains(it)) {
                        escapedString = escapedString.replace(it, "\\$it")
                    }
                }
            }
            return Regex(escapedString.replace("*", ".*"))
        }
    }
}
