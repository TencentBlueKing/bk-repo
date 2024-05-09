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

package com.tencent.bkrepo.auth.interceptor

import com.tencent.bkrepo.auth.constant.AUTH_API_USER_UPDATE_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_ROLE_SYS_LIST_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_PERMISSION_LIST_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_PERMISSION_USER_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_USER_LIST_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_INFO_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_PROJECT_ADMIN_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_USER_INFO_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_TOKEN_LIST_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_TOKEN_PREFIX
import com.tencent.bkrepo.auth.constant.AUTHORIZATION
import com.tencent.bkrepo.auth.constant.AUTH_API_ACCOUNT_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_EXT_PERMISSION_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_KEY_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_API_OAUTH_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_FAILED_RESPONSE
import com.tencent.bkrepo.auth.constant.AUTH_PROJECT_SUFFIX
import com.tencent.bkrepo.auth.constant.AUTH_REPO_SUFFIX
import com.tencent.bkrepo.auth.constant.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.auth.constant.PLATFORM_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.constant.ADMIN_USER
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.constant.AUTH_HEADER_UID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.HandlerInterceptor
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AuthInterceptor : HandlerInterceptor {

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var userService: UserService

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authHeader = request.getHeader(AUTHORIZATION).orEmpty()
        var authFailStr = String.format(AUTH_FAILED_RESPONSE, authHeader)
        try {
            // basic认证
            if (authHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) {
                return checkUserFromBasic(request, authHeader)
            }

            // platform认证
            authFailStr = String.format(AUTH_FAILED_RESPONSE, "illegal AUTHORIZATION")
            return checkUserFromPlatform(request, authHeader)
        } catch (e: IllegalArgumentException) {
            response.status = HttpStatus.UNAUTHORIZED.value
            response.writer.print(authFailStr)
            logger.warn("check exception [$e]")
            return false
        }
    }

    private fun checkUserFromBasic(request: HttpServletRequest, authHeader: String): Boolean {
        val projectAccess = userProjectApiSet.any { request.requestURI.contains(it) }
        val userAccess = userAccessApiSet.any { request.requestURI.contains(it) }
        val encodedCredentials = authHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
        val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
        val parts = decodedHeader.split(COLON)
        require(parts.size == 2)
        val user = userService.findUserByUserToken(parts[0], parts[1]) ?: run {
            logger.warn("find no user [${parts[0]}]")
            throw IllegalArgumentException("check credential fail")
        }

        request.setAttribute(USER_KEY, parts[0])
        request.setAttribute(ADMIN_USER, user.admin)
        // 非管理员访问非授权endpoint
        if (!user.admin && !(projectAccess || userAccess)) {
            logger.warn("user [${parts[0]}] can not access this endpoint [${request.requestURI}]")
            throw IllegalArgumentException("check credential fail")
        }
        return true
    }

    private fun checkUserFromPlatform(request: HttpServletRequest, authHeader: String): Boolean {
        // platform认证
        val encodedCredentials = authHeader.removePrefix(PLATFORM_AUTH_HEADER_PREFIX)
        val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
        val userAccess = userAccessApiSet.any { request.requestURI.contains(it) }
        val parts = decodedHeader.split(COLON)
        require(parts.size == 2)
        val appId = accountService.checkCredential(parts[0], parts[1]) ?: run {
            logger.warn("find no account [$parts[0]]")
            throw IllegalArgumentException("check auth credential fail")
        }
        val userId = request.getHeader(AUTH_HEADER_UID).orEmpty().trim()
        if (userId.isEmpty()) {
            logger.warn("platform auth with empty userId")
            throw IllegalArgumentException("userId is empty")
        }
        val userInfo = userService.getUserInfoById(userId)
        val isAdmin: Boolean
        if (userId.isNotEmpty() && userInfo == null) {
            val createRequest = CreateUserRequest(userId = userId, name = userId)
            isAdmin = false
            userService.createUser(createRequest)
        } else {
            isAdmin = userInfo!!.admin
        }

        if (!isAdmin && !userAccess) {
            logger.warn("user [$userId] can not access the endpoint [${request.requestURI}]")
            throw IllegalArgumentException("user access admin endpoint")
        }
        logger.debug("auth userId [$userId], platId [$appId]")
        request.setAttribute(USER_KEY, userId)
        request.setAttribute(PLATFORM_KEY, appId)
        request.setAttribute(ADMIN_USER, isAdmin)
        return true
    }

    companion object {

        private val logger = LoggerFactory.getLogger(AuthInterceptor::class.java)

        // 项目内权限校验api,开放给basic访问
        private val userProjectApiSet = setOf(
            AUTH_REPO_SUFFIX,
            AUTH_PROJECT_SUFFIX,
            AUTH_API_ACCOUNT_PREFIX,
            AUTH_API_KEY_PREFIX,
            AUTH_API_OAUTH_PREFIX,
            AUTH_API_EXT_PERMISSION_PREFIX
        )

        // 普通用户可访问api,开放给basic and platform用户访问
        private val userAccessApiSet = setOf(
            AUTH_API_PROJECT_ADMIN_PREFIX,
            AUTH_API_USER_INFO_PREFIX,
            AUTH_API_TOKEN_LIST_PREFIX,
            AUTH_API_TOKEN_PREFIX,
            AUTH_API_USER_LIST_PREFIX,
            AUTH_API_INFO_PREFIX,
            AUTH_API_ROLE_SYS_LIST_PREFIX,
            AUTH_API_PERMISSION_LIST_PREFIX,
            AUTH_API_PERMISSION_USER_PREFIX,
            AUTH_API_USER_UPDATE_PREFIX
        )
    }
}
