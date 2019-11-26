package com.tencent.bkrepo.common.artifact.auth

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
     * 是否需要进行认证
     */
    fun needAuthenticate(uri: String, projectId: String?, repoName: String?): Boolean

    /**
     * 进行认证
     * 认证成功返回用户id，失败则抛ClientAuthException异常
     */
    @Throws(ClientAuthException::class)
    fun onAuthenticate(request: HttpServletRequest): String

    /**
     * 认证失败回调
     * 可以根据各自依赖源的协议返回不同的数据格式
     */
    fun onAuthenticateFailed(request: HttpServletRequest, response: HttpServletResponse)

    /**
     * 认证成功回调
     */
    fun onAuthenticateSuccess(userId: String, request: HttpServletRequest, response: HttpServletResponse)
}