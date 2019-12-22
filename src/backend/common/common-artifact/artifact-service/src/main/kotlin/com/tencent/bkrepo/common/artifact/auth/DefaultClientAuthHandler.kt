package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.common.artifact.config.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class DefaultClientAuthHandler : ClientAuthHandler {

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        if (basicAuthHeader.isNullOrBlank()) return AnonymousCredentials()

        try {
            if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(":")
            require(parts.size >= 2)
            return BasicAuthCredentials(parts[0], parts[1])
        } catch (exception: Exception) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        val basicAuthCredentials = authCredentials as BasicAuthCredentials
        val userId = request.getHeader(AUTH_HEADER_USER_ID)
        //  TODO: header方式传递进来的直接通过
        if (userId != null) {
            return userId
        }
        // TODO: auth 进行认证
        return basicAuthCredentials.username
    }

    override fun onAuthenticateFailed(response: HttpServletResponse, clientAuthException: ClientAuthException) {
        // 默认向上抛异常，由ArtifactExceptionHandler统一处理
        throw clientAuthException
    }

    override fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {}
}
