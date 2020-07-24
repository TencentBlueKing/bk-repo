package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.AuthProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(authProperties: AuthProperties) {

    init {
        jwtProperties = authProperties.jwt
        val left = SHA_256_SECRET_CHAR_SIZE - jwtProperties.secretKey.length
        val builder = StringBuilder(jwtProperties.secretKey)
        for (i in 0 until left) {
            builder.append(i % 10)
        }
        secretKey = Keys.hmacShaKeyFor(builder.toString().toByteArray())
    }

    companion object {
        private const val SHA_256_SECRET_CHAR_SIZE = 256 / 8

        private lateinit var jwtProperties: AuthProperties.JwtProperties
        private lateinit var secretKey: SecretKey

        fun generateToken(userId: String, claims: MutableMap<String, Any>): String {
            val now = Date()
            val expiration = jwtProperties.expireSeconds.takeIf { it > 0 }?.let { Date(now.time + it * 1000) }
            return Jwts.builder()
                .setIssuedAt(now)
                .setSubject(userId)
                .setExpiration(expiration)
                .addClaims(claims)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact()
        }

        fun validateToken(token: String): Jws<Claims> {
            return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token)
        }
    }
}
