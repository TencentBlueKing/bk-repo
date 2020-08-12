package com.tencent.bkrepo.common.security.http.platform

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.security.constant.PLATFORM_AUTH_PREFIX
import com.tencent.bkrepo.common.security.exception.BadCredentialsException
import com.tencent.bkrepo.common.security.http.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import org.slf4j.LoggerFactory
import java.util.Base64
import javax.servlet.http.HttpServletRequest

/**
 * 平台账号认证
 */
open class PlatformAuthHandler(
    private val authenticationManager: AuthenticationManager,
    private val serviceUserResource: ServiceUserResource
) : HttpAuthHandler {

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(PLATFORM_AUTH_PREFIX)) {
            try {
                val encodedCredentials = authorizationHeader.removePrefix(PLATFORM_AUTH_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(StringPool.COLON)
                require(parts.size >= 2)
                PlatformAuthCredentials(parts[0], parts[1])
            } catch (exception: IllegalArgumentException) {
                throw BadCredentialsException("Authorization value [$authorizationHeader] is not a valid scheme.")
            }
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        with(authCredentials as PlatformAuthCredentials) {
            val appId = authenticationManager.checkPlatformAccount(accessKey, secretKey)
            val userId = request.getHeader(AUTH_HEADER_UID)?.apply { checkUserId(this) } ?: appId
            request.setAttribute(PLATFORM_KEY, appId)
            request.setAttribute(USER_KEY, userId)
            return userId
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
        private val logger = LoggerFactory.getLogger(PlatformAuthHandler::class.java)
    }
}
