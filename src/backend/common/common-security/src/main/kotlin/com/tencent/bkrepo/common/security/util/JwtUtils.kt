package com.tencent.bkrepo.common.security.util

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import java.security.Key
import java.time.Duration
import java.util.Date

/**
 * Json web token 工具类
 */
object JwtUtils {

    private val SIGNATURE_ALGORITHM = SignatureAlgorithm.HS512
    private val SECRET_KEY_MIN_LENGTH = SIGNATURE_ALGORITHM.minKeyLength / 8

    fun generateToken(signingKey: Key, expireDuration: Duration, subject: String? = null, claims: Map<String, Any>? = null): String {
        val now = Date()
        val expiration = expireDuration.toMillis().takeIf { it > 0 }?.let { Date(now.time + it) }
        return Jwts.builder()
            .setIssuedAt(now)
            .setSubject(subject)
            .setExpiration(expiration)
            .addClaims(claims)
            .signWith(signingKey, SIGNATURE_ALGORITHM)
            .compact()
    }

    @Throws(
        ExpiredJwtException::class,
        UnsupportedJwtException::class,
        MalformedJwtException::class,
        SignatureException::class,
        IllegalArgumentException::class
    )
    fun validateToken(signingKey: Key, token: String): Jws<Claims> {
        return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token)
    }

    fun createSigningKey(secretKey: String): Key {
        return Keys.hmacShaKeyFor(secretKey.padEnd(SECRET_KEY_MIN_LENGTH).toByteArray())
    }
}
