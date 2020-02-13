package com.tencent.bkrepo.common.artifact.auth.platform

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.auth.AnonymousCredentials
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.slf4j.LoggerFactory
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
open class PlatformClientAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var serviceAccountResource: ServiceAccountResource

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER) ?: ""
        return if (basicAuthHeader.startsWith(PLATFORM_AUTH_HEADER_PREFIX)) {
            try {
                val encodedCredentials = basicAuthHeader.removePrefix(PLATFORM_AUTH_HEADER_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(":")
                require(parts.size >= 2)
                PlatformAuthCredentials(parts[0], parts[1])
            } catch (exception: Exception) {
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme.")
            }
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        with(authCredentials as PlatformAuthCredentials) {
            serviceAccountResource.checkCredential(accessKey, secretKey).data?.run {
                val userId = request.getHeader(AUTH_HEADER_UID)?.let {
                    checkUserId(it)
                    it
                } ?: this
                request.setAttribute(APP_KEY, this)
                request.setAttribute(USER_KEY, userId)
                return userId
            } ?: throw ClientAuthException("AccessKey/SecretKey check failed.")
        }
    }

    private fun checkUserId(userId: String) {
        if (serviceUserResource.detail(userId).data == null) {
            val request = CreateUserRequest(userId = userId, name = userId)
            serviceUserResource.createUser(request)
            logger.info("Create user [$request] success.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PlatformClientAuthHandler::class.java)
        private const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "
    }
}
