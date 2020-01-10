package com.tencent.bkrepo.docker.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Claim
import java.util.Date

/**
 * Jwt 工具类
 *
 * @author owenlxu
 */
open class JwtUtil {

    companion object {

        private val secret = "bkrepo"
        private val expireTime = 15 * 60 * 1000

        fun sign(username: String): String {
            var createDate = Date()
            var expireDate = Date(System.currentTimeMillis() + expireTime)

            var header = mapOf("alg" to "HS512", "typ" to "JWT")
            return JWT.create()
                .withHeader(header)
                .withIssuedAt(createDate)
                .withExpiresAt(expireDate)
                .withClaim("username", username)
                .sign(Algorithm.HMAC512(secret))
        }

        fun parse(token: String): MutableMap<String, Claim>? {
            return JWT.decode(token).claims
        }

        fun verifyToken(token: String): Boolean {
            return try {
                JWT.require(Algorithm.HMAC512(secret)).build().verify(token)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun getUserName(token: String): String {
            return JWT.decode(token).getClaim("username").asString()
        }

        fun refresh(token: String): String {
            var createDate = Date()
            var expireDate = Date(System.currentTimeMillis() + expireTime)

            val username = getUserName(token)
            var header = mapOf("alg" to "HS512", "typ" to "JWT")
            return JWT.create()
                .withHeader(header)
                .withIssuedAt(createDate)
                .withExpiresAt(expireDate)
                .withClaim("username", username)
                .sign(Algorithm.HMAC512(secret))
        }
    }
}
