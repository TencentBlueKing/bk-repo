package com.tencent.bkrepo.common.service.security

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.Date

@Component
class SecurityAuthenticateManager(
    properties: SecurityProperties
) {
    private var token: String? = null
    private val signingKey = Keys.hmacShaKeyFor(properties.secretKey.padEnd(SECRET_KEY_MIN_LENGTH).toByteArray())

    fun getSecurityToken(): String {
        return token ?: generateSecurityToken()
    }

    fun verifySecurityToken(token: String) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token)
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
        val now = Date()
        token = Jwts.builder()
            .setIssuedAt(now)
            .setExpiration(Date(now.time + TOKEN_EXPIRATION))
            .signWith(signingKey, SignatureAlgorithm.HS512)
            .compact()
        return token.orEmpty()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityAuthenticateManager::class.java)
        private const val SECRET_KEY_MIN_LENGTH = 512 / 8
        private const val TOKEN_EXPIRATION = 10 * 60 * 1000L
        private const val REFRESH_DELAY = TOKEN_EXPIRATION - 1000L
    }
}
