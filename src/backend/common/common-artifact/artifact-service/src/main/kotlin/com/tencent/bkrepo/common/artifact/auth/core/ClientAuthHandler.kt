package com.tencent.bkrepo.common.artifact.auth.core

import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 *
 * @author: carrypan
 * @date: 2019/11/22
 */
interface ClientAuthHandler {

    /**
     * 提取认证身份信息
     */
    fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials

    /**
     * 进行认证
     * 认证成功返回用户id，失败则抛ClientAuthException异常
     */
    @Throws(ClientAuthException::class)
    fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String

    /**
     * 认证失败回调
     * 可以根据各自依赖源的协议返回不同的数据格式
     */
    fun onAuthenticateFailed(response: HttpServletResponse, clientAuthException: ClientAuthException) {
        // 默认向上抛异常，由ArtifactExceptionHandler统一处理
        throw clientAuthException
    }

    /**
     * 认证成功回调
     */
    fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {}
}
