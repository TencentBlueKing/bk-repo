package com.tencent.bkrepo.common.security.http.login

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.security.constant.AUTHORIZATION
import com.tencent.bkrepo.common.security.constant.BEARER_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.basic.BasicAuthHandler
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.util.JwtUtils
import java.time.Duration
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * BasicAuth登录example，登录成功后返回jwt token
 */
class BasicAuthLoginHandler(authenticationManager: AuthenticationManager) : BasicAuthHandler(authenticationManager) {

    private val signingKey = JwtUtils.createSigningKey(StringPool.DOT)

    override fun getLoginEndpoint() = "/login"

    override fun onAuthenticateSuccess(request: HttpServletRequest, response: HttpServletResponse, userId: String) {
        val token = JwtUtils.generateToken(signingKey, Duration.ZERO, userId)
        response.addHeader(AUTHORIZATION, "$BEARER_AUTH_HEADER_PREFIX $token")
        super.onAuthenticateSuccess(request, response, userId)
    }

    override fun onAuthenticateFailed(request: HttpServletRequest, response: HttpServletResponse, authenticationException: AuthenticationException) {
        super.onAuthenticateFailed(request, response, authenticationException)
    }
}
