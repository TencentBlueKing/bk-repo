package com.tencent.bkrepo.npm.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Claim
import com.tencent.bkrepo.npm.exception.NpmTokenIllegalException
import java.util.Calendar
import java.util.Date

object JwtUtils {
    /* 秘钥 */
    private const val secret = "bk_repo"

    /** token 过期时间: 30天  */
    private const val calendarField = Calendar.DATE
    private const val calendarInterval = 30

    fun sign(username: String, password: String): String {
        val createDate = Date()
        val nowTime = Calendar.getInstance()
        nowTime.add(calendarField, calendarInterval)
        val expireDate = nowTime.time

        val map = mapOf("alg" to "HS512", "typ" to "JWT")
        return JWT.create()
            .withHeader(map)
            .withClaim("username", username)
            //.withClaim("password", password)
            .withIssuedAt(createDate)
            .withExpiresAt(expireDate)
            .sign(Algorithm.HMAC512(secret))
    }

    /**
     * 解密Token
     *
     * @param token
     * @return
     * @throws Exception
     */
    fun verifyToken(token: String): Map<String, Claim> {
        return try {
            val verifier = JWT.require(Algorithm.HMAC512(secret)).build()
            verifier.verify(token).claims
        } catch (e: Exception) {
            // token 校验失败, 抛出Token验证非法异常
            throw NpmTokenIllegalException("bad props auth token")
        }
    }

    /**
     * 获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    fun getUserName(token: String): String {
        val claims = verifyToken(token)
        return (claims["username"] ?: error("")).asString()
    }

    /**
     * 获取密码
     *
     * @param token 令牌
     * @return 密码
     */
    fun getPassword(token: String): String {
        val claims = verifyToken(token)
        return (claims["password"] ?: error("")).asString()
    }
}