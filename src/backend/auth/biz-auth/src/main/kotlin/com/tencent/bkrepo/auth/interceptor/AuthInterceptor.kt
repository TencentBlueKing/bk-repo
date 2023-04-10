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
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import com.tencent.bkrepo.common.service.util.HttpSigner
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.apache.commons.codec.digest.HmacAlgorithms
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AuthInterceptor(
    private val httpAuthSecurity: HttpAuthSecurity
) : HandlerInterceptor {

    private val accountService: AccountService by lazy { SpringContextUtils.getBean() }

    private val userService: UserService by lazy { SpringContextUtils.getBean() }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authHeader = request.getHeader(AUTHORIZATION).orEmpty()
        val authFailStr = String.format(AUTH_FAILED_RESPONSE, authHeader)
        try {
            val urlMatch = basicAuthApiSet.filter { request.requestURI.contains(it) }.size

            // basic认证
            if (authHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) {
                return basicAuth(authHeader, request, urlMatch)
            }

            // platform认证
            if (authHeader.startsWith(PLATFORM_AUTH_HEADER_PREFIX)) {
                return platformAuth(authHeader, request)
            }

            // sign认证
            val sig = request.getParameter(HttpSigner.SIGN)
            val appId = request.getParameter(HttpSigner.APP_ID)
            val accessKey = request.getParameter(HttpSigner.ACCESS_KEY)
            if (sig != null && appId != null && accessKey != null) {
                return signAuth(request)
            }

            throw IllegalArgumentException("invalid auth type")
        } catch (e: IllegalArgumentException) {
            response.status = HttpStatus.UNAUTHORIZED.value
            response.writer.print(authFailStr)
            logger.warn("check exception [$e]")
            return false
        }
    }

    private fun signAuth(request: HttpServletRequest): Boolean {
        val sig = request.getParameter(HttpSigner.SIGN)
        val appId = request.getParameter(HttpSigner.APP_ID)
        val accessKey = request.getParameter(HttpSigner.ACCESS_KEY)

        val secretKey = accountService.findSecretKey(appId, accessKey)
        // 账号非法
            ?: throw AuthenticationException("AppId or accessKey error.")
        val uri = getUrlPath(request)
        val bodyHash = request.getAttribute(HttpSigner.SIGN_BODY).toString()
        val computeSig = HttpSigner.sign(request, uri, bodyHash, secretKey, HmacAlgorithms.HMAC_SHA_1.getName())
        if (computeSig != sig) {
            // 签名未通过
            val signatureStr = HttpSigner.getSignatureStr(request, uri, bodyHash)
            throw AuthenticationException("Invalid signature, server signature string: $signatureStr")
        }
        val signTime = request.getParameter(HttpSigner.SIGN_TIME)
        val expiredTime = signTime.split(HttpSigner.TIME_SPLIT).last().toLong()
        if (expiredTime < System.currentTimeMillis() / HttpSigner.MILLIS_PER_SECOND) {
            // 请求超时
            throw PermissionException("Request timeout.")
        }
        val userId = request.getHeader(MS_AUTH_HEADER_UID) ?: SYSTEM_USER
        setAuthAttribute(userId, appId, request)
        return true
    }

    private fun platformAuth(authHeader: String, request: HttpServletRequest): Boolean {
        val encodedCredentials = authHeader.removePrefix(PLATFORM_AUTH_HEADER_PREFIX)
        val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
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
        setAuthAttribute(userId, appId, request)
        return true
    }

    private fun setAuthAttribute(userId: String, appId: String, request: HttpServletRequest) {
        val userInfo = userService.getUserInfoById(userId)
        val isAdmin: Boolean = userInfo?.admin ?: false
        if (userId.isNotEmpty() && userInfo == null && userId != ANONYMOUS_USER) {
            val createRequest = CreateUserRequest(userId = userId, name = userId)
            userService.createUser(createRequest)
        }
        logger.debug("auth userId [$userId], platId [$appId]")
        request.setAttribute(USER_KEY, userId)
        request.setAttribute(PLATFORM_KEY, appId)
        request.setAttribute(ADMIN_USER, isAdmin)
    }

    private fun basicAuth(
        basicAuthHeader: String,
        request: HttpServletRequest,
        urlMatch: Int
    ): Boolean {
        val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
        val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
        val parts = decodedHeader.split(COLON)
        require(parts.size == 2)
        val user = userService.findUserByUserToken(parts[0], parts[1]) ?: run {
            logger.warn("find no user [${parts[0]}]")
            throw IllegalArgumentException("check credential fail")
        }

        request.setAttribute(USER_KEY, parts[0])
        request.setAttribute(ADMIN_USER, user.admin)
        // 非项目内认证账号
        if (urlMatch == 0 && !user.admin) {
            logger.warn("user [${parts[0]}] is not admin")
            throw IllegalArgumentException("check credential fail")
        }
        return true
    }

    private fun getUrlPath(request: HttpServletRequest): String {
        val realPath = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        var path = realPath
        if (httpAuthSecurity.prefixEnabled) {
            path = realPath.removePrefix(httpAuthSecurity.prefix)
        }
        return path
    }

    companion object {

        private val logger = LoggerFactory.getLogger(AuthInterceptor::class.java)

        private val basicAuthApiSet = setOf(
            AUTH_REPO_SUFFIX,
            AUTH_PROJECT_SUFFIX,
            AUTH_API_ACCOUNT_PREFIX,
            AUTH_API_KEY_PREFIX,
            AUTH_API_OAUTH_PREFIX,
            AUTH_API_EXT_PERMISSION_PREFIX
        )
    }
}
