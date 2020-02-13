package com.tencent.bkrepo.common.artifact.auth.basic

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.artifact.auth.AnonymousCredentials
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import java.util.Base64
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
@Order(Ordered.LOWEST_PRECEDENCE)
open class BasicClientAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER) ?: ""
        return if (basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) {
            try {
                val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(":")
                require(parts.size >= 2)
                BasicAuthCredentials(parts[0], parts[1])
            } catch (exception: Exception) {
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme.")
            }
        } else AnonymousCredentials()
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
