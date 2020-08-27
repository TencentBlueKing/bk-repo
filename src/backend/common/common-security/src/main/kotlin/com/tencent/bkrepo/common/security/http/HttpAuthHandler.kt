package com.tencent.bkrepo.common.security.http

import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.BadCredentialsException
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface HttpAuthHandler {

    /**
     * 登录endpoint，表示该handler用于处理登录请求
     * 默认返回null, 表示在所有请求都进行认证
     * 支持ant路径匹配规则
     */
    fun getLoginEndpoint(): String? = null

    /**
     * 提取认证身份信息
     */
    @Throws(BadCredentialsException::class)
    fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials

    /**
     * 进行认证
     * 认证成功返回用户id，失败则抛AuthenticationException异常
     */
    @Throws(AuthenticationException::class)
    fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String

    /**
     * 认证失败回调
     * 可以根据各自依赖源的协议返回不同的数据格式
     */
    fun onAuthenticateFailed(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authenticationException: AuthenticationException
    ) {
        // 默认向上抛异常
        throw authenticationException
    }

    /**
     * 认证成功回调
     */
    fun onAuthenticateSuccess(request: HttpServletRequest, response: HttpServletResponse, userId: String) { }
}
