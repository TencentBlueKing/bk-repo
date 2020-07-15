package com.tencent.bkrepo.docker.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.Claim
import com.tencent.bkrepo.docker.constant.REGISTRY_SERVICE
import java.util.Date

/**
 * jwt protocol utility
 * @author: owenlxu
 * @date: 2019-11-15
 */
object JwtUtil {

    private const val secret = REGISTRY_SERVICE
    private const val expireTime = 15 * 60 * 1000

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
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: JWTVerificationException) {
            false
        }
    }

    fun getUserName(token: String): String {
        return JWT.decode(token).getClaim("username").asString()
    }
}
