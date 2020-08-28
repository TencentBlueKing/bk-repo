package com.tencent.bkrepo.common.security.http.basic

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.security.exception.BadCredentialsException
import com.tencent.bkrepo.common.security.http.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import java.util.Base64
import javax.servlet.http.HttpServletRequest

/**
 * Basic Http 认证方式
 */
open class BasicAuthHandler(private val authenticationManager: AuthenticationManager) : HttpAuthHandler {

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(BASIC_AUTH_PREFIX)) {
            try {
                val encodedCredentials = authorizationHeader.removePrefix(BASIC_AUTH_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(StringPool.COLON)
                require(parts.size >= 2)
                BasicAuthCredentials(parts[0], parts[1])
            } catch (exception: IllegalArgumentException) {
                throw BadCredentialsException("Authorization value [$authorizationHeader] is not a valid scheme.")
            }
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        with(authCredentials as BasicAuthCredentials) {
            return authenticationManager.checkUserAccount(username, password)
        }
    }
}
