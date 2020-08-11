package com.tencent.bkrepo.common.security.http.jwt

import com.tencent.bkrepo.common.security.constant.AUTHORIZATION
import com.tencent.bkrepo.common.security.constant.BEARER_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.BadCredentialsException
import com.tencent.bkrepo.common.security.http.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.util.JwtUtils
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import javax.servlet.http.HttpServletRequest

open class JwtAuthHandler(properties: JwtAuthProperties) : HttpAuthHandler {

    private val signingKey = JwtUtils.createSigningKey(properties.secretKey)

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val authorizationHeader = request.getHeader(AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(BEARER_AUTH_HEADER_PREFIX)) {
            val jwtToken = authorizationHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX).trim()
            return JwtAuthCredentials(jwtToken)
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        with(authCredentials as JwtAuthCredentials) {
            try {
                return JwtUtils.validateToken(signingKey, token).body.subject
            } catch (exception: ExpiredJwtException) {
                throw AuthenticationException("Expired token")
            } catch (exception: JwtException) {
                throw BadCredentialsException("Invalid token")
            } catch (exception: IllegalArgumentException) {
                throw BadCredentialsException("Empty token")
            }
        }
    }
}
