/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.fs.server.handler

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.common.api.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.interceptor.devx.DevXWorkSpace
import com.tencent.bkrepo.fs.server.api.RAuthClient
import com.tencent.bkrepo.fs.server.constant.JWT_CLAIMS_PERMIT
import com.tencent.bkrepo.fs.server.constant.JWT_CLAIMS_REPOSITORY
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.pojo.DevxLoginResponse
import com.tencent.bkrepo.fs.server.request.IoaLoginRequest
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.utils.DevxWorkspaceUtils
import com.tencent.bkrepo.fs.server.utils.IoaUtils
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils.bearerToken
import com.tencent.bkrepo.fs.server.utils.SecurityManager
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

/**
 * 登录处理器
 * */
class LoginHandler(
    private val permissionService: PermissionService,
    private val securityManager: SecurityManager,
    private val rAuthClient: RAuthClient,
) {

    /**
     * 登录请求
     * */
    suspend fun login(request: ServerRequest): ServerResponse {
        val projectId = request.pathVariable(PROJECT_ID)
        val repoName = request.pathVariable(REPO_NAME)

        val authorizationHeader = request.headers().header(HttpHeaders.AUTHORIZATION).firstOrNull().orEmpty()

        if (!authorizationHeader.startsWith(BASIC_AUTH_PREFIX)) {
            throw AuthenticationException()
        }
        val (username, password) = BasicAuthUtils.decode(authorizationHeader)
        val tokenRes = rAuthClient.checkToken(username, password).awaitSingle()
        if (tokenRes.data != true) {
            throw AuthenticationException()
        }
        val token = createToken(projectId, repoName, username)
        return ReactiveResponseBuilder.success(token)
    }

    suspend fun devxLogin(request: ServerRequest): ServerResponse {
        val workspace = DevxWorkspaceUtils.getWorkspace().awaitSingleOrNull() ?: throw AuthenticationException()
        val repoName = request.pathVariable(REPO_NAME)
        val userId = createUser(workspace)
        val token = createToken(workspace.projectId, repoName, userId)
        val response = DevxLoginResponse(workspace.projectId, token)
        return ReactiveResponseBuilder.success(response)
    }

    suspend fun ioaLogin(request: ServerRequest): ServerResponse {
        val projectId = request.pathVariable("projectId")
        val repoName = request.pathVariable("repoName")
        val ioaLoginRequest = request.bodyToMono(IoaLoginRequest::class.java).awaitSingle()
        val userId = ioaLoginRequest.userName
        IoaUtils.checkTicket(ioaLoginRequest)
        createUser(userId)
        val token = createToken(projectId, repoName, userId)
        return ReactiveResponseBuilder.success(token)
    }

    suspend fun ioaTicket(request: ServerRequest): ServerResponse {
        return ServerResponse.ok().bodyValueAndAwait(IoaUtils.proxyTicketRequest(request))
    }

    private suspend fun createUser(userName: String) {
        val request = CreateUserRequest(userId = userName, name = userName)
        rAuthClient.create(request).awaitSingle()
    }

    private suspend fun createUser(workspace: DevXWorkSpace): String {
        return if (workspace.realOwner.isNotBlank()) {
            createUser(workspace.realOwner)
            workspace.realOwner
        } else {
            val userId = "g_${workspace.projectId}"
            val request = CreateUserToProjectRequest(
                userId = userId,
                name = userId,
                group = true,
                asstUsers = listOf(workspace.creator),
                projectId = workspace.projectId
            )
            rAuthClient.createUserToProject(request).awaitSingle()
            userId
        }
    }

    private suspend fun createToken(projectId: String, repoName: String, username: String): String {
        val claims = mutableMapOf(JWT_CLAIMS_REPOSITORY to "$projectId/$repoName")
        val writePermit = permissionService.checkPermission(projectId, repoName, PermissionAction.WRITE, username)
        if (writePermit) {
            claims[JWT_CLAIMS_PERMIT] = PermissionAction.WRITE.name
        } else {
            val repoDetail = ReactiveArtifactContextHolder.getRepoDetail()
            val readPermit = repoDetail.public ||
                permissionService.checkPermission(projectId, repoName, PermissionAction.READ, username)
            if (readPermit) {
                claims[JWT_CLAIMS_PERMIT] = PermissionAction.READ.name
            }
        }
        val token = securityManager.generateToken(
            subject = username,
            claims = claims,
        )
        return token
    }

    suspend fun refresh(request: ServerRequest): ServerResponse {
        val token = request.bearerToken().orEmpty()
        val jws = securityManager.validateToken(token)
        val claims = jws.body
        val username = claims.subject
        val parts = claims[JWT_CLAIMS_REPOSITORY].toString().split("/")
        val projectId = parts[0]
        val repoName = parts[1]
        val newToken = createToken(projectId, repoName, username)
        return ReactiveResponseBuilder.success(newToken)
    }
}
