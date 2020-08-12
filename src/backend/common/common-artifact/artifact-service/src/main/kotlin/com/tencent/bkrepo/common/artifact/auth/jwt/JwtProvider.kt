package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.AuthProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.security.Key
import java.util.Date

@Component
class JwtProvider(authProperties: AuthProperties) {

    init {
        jwtProperties = authProperties.jwt
        signingKey = Keys.hmacShaKeyFor(jwtProperties.secretKey.padEnd(SECRET_KEY_MIN_LENGTH).toByteArray())
    }

    companion object {
        private const val SECRET_KEY_MIN_LENGTH = 512 / 8
        private lateinit var jwtProperties: AuthProperties.JwtProperties
        private lateinit var signingKey: Key

        fun generateToken(userId: String, claims: MutableMap<String, Any>): String {
            val now = Date()
            val expirationMillis = jwtProperties.expiration.toMillis()
            val expiration = expirationMillis.takeIf { it > 0 }?.let { Date(now.time + it) }
            return Jwts.builder()
                .setIssuedAt(now)
                .setSubject(userId)
                .setExpiration(expiration)
                .addClaims(claims)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact()
        }

        fun validateToken(token: String): Jws<Claims> {
            return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token)
        }
    }
}
