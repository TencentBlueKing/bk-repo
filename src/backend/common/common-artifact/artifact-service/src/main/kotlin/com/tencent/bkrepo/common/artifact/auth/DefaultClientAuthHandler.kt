package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.springframework.beans.factory.annotation.Autowired
import java.util.Base64
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class DefaultClientAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        if (basicAuthHeader.isNullOrBlank()) return AnonymousCredentials()

        try {
            if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(":")
            require(parts.size >= 2)
            require(parts[0].isNotBlank())
            return BasicAuthCredentials(parts[0], parts[1])
        } catch (exception: Exception) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        with(authCredentials as BasicAuthCredentials) {
            val response = serviceUserResource.checkUserToken(username, password)
            if (response.data == true) {
                return username
            } else {
                throw ClientAuthException("Authorization value check failed")
            }
        }
    }
}
