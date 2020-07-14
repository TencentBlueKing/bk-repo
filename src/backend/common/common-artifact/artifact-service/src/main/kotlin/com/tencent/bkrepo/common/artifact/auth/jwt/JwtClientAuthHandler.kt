package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.core.AnonymousCredentials
import com.tencent.bkrepo.common.artifact.auth.core.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.core.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.config.BEARER_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
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
            val jwtToken = basicAuthHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX).trim()
            return JwtAuthCredentials(jwtToken)
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        with(authCredentials as JwtAuthCredentials) {
            try {
                return jwtProvider.validateToken(token).body.subject
            } catch (exception: ExpiredJwtException) {
                throw ClientAuthException("Expired token")
            } catch (exception: JwtException) {
                throw ClientAuthException("Invalid token")
            } catch (exception: IllegalArgumentException) {
                throw ClientAuthException("Empty token")
            }
        }
    }
}
