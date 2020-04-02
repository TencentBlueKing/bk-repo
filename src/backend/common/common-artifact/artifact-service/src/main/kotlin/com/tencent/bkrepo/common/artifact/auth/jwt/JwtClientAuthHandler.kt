package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.AnonymousCredentials
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.config.BEARER_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import javax.servlet.http.HttpServletRequest

@Order(Ordered.LOWEST_PRECEDENCE)
open class JwtClientAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(AUTHORIZATION).orEmpty()
        return if (basicAuthHeader.startsWith(BEARER_AUTH_HEADER_PREFIX)) {
            try {
                val jwtToken = basicAuthHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX).trim()
                return JwtAuthCredentials(jwtToken)
            } catch (exception: Exception) {
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme.")
            }
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        with(authCredentials as JwtAuthCredentials) {
            try {
                return jwtProvider.validateToken(token).body.subject
            } catch (exception: Exception) {
                throw ClientAuthException(exception.message.orEmpty())
            }
        }
    }
}
