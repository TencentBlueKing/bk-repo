package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.config.AuthProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date

@Component
class JwtProvider {

    @Autowired
    private lateinit var authProperties: AuthProperties

    private val secretKey = Keys.hmacShaKeyFor(authProperties.jwt.secretKey.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(userId: String, claims: Map<String, Any>): String {
        val now = Date()
        val expiration = authProperties.jwt.expireSeconds.takeIf { it > 0 }?.let { Date(now.time + it * 1000) }
        return Jwts.builder()
            .setIssuedAt(now)
            .setSubject(userId)
            .setExpiration(expiration)
            .setClaims(claims)
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact()
    }

    fun validateToken(token: String): Jws<Claims> {
        try {
            return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token)
        } catch (exception: ExpiredJwtException) {
            throw RuntimeException("Expired token.")
        } catch (exception: Exception) {
            throw RuntimeException("Invalid token.")
        }
    }

}