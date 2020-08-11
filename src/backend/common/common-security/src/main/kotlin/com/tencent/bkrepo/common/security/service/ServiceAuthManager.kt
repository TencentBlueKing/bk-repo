package com.tencent.bkrepo.common.security.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.JwtUtils
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ServiceAuthManager(
    properties: ServiceAuthProperties
) {
    private var token: String? = null
    private val signingKey = JwtUtils.createSigningKey(properties.secretKey)

    fun getSecurityToken(): String {
        return token ?: generateSecurityToken()
    }

    fun verifySecurityToken(token: String) {
        try {
            JwtUtils.validateToken(signingKey, token)
        } catch (exception: ExpiredJwtException) {
            throw ErrorCodeException(CommonMessageCode.SERVICE_UNAUTHENTICATED, "Expired token")
        } catch (exception: JwtException) {
            throw ErrorCodeException(CommonMessageCode.SERVICE_UNAUTHENTICATED, "Invalid token")
        } catch (exception: IllegalArgumentException) {
            throw ErrorCodeException(CommonMessageCode.SERVICE_UNAUTHENTICATED, "Empty token")
        }
    }

    @Scheduled(fixedDelay = REFRESH_DELAY)
    fun refreshSecurityToken() {
        logger.info("Refreshing security token")
        generateSecurityToken()
    }

    private fun generateSecurityToken(): String {
        token = JwtUtils.generateToken(signingKey, Duration.ofMillis(TOKEN_EXPIRATION))
        return token.orEmpty()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceAuthManager::class.java)
        private const val TOKEN_EXPIRATION = 10 * 60 * 1000L
        private const val REFRESH_DELAY = TOKEN_EXPIRATION - 1000L
    }
}
