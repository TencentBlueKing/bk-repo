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

        /* 秘钥 */
        private val secret = "bkrepo"
        /* 过期时间 15 分钟 */
        private val expireTime = 15 * 60 * 1000

        /**
         * 生成Token
         *
         * @param username 用户名
         * @param password 用户密码
         * @return token令牌
         */
        fun sign(username: String, password: String): String {
            var createDate = Date()
            var expireDate = Date(System.currentTimeMillis() + expireTime)

            var claims = mapOf("username" to username, "password" to password)
            return JWT.create()
                    .withHeader(claims)
                    .withIssuedAt(createDate)
                    .withExpiresAt(expireDate)
                    .withClaim("username", username)
                    .withClaim("password", password)
                    .sign(Algorithm.HMAC512(secret))
        }

        /**
         * 解析Token
         *
         * @param token 令牌
         * @return claim
         */
        fun parse(token: String): MutableMap<String, Claim>? {
            return JWT.decode(token).claims
        }

        /**
         * 检验Token
         *
         * @param token 令牌
         * @return 有效true 无效false
         */
        fun verifyToken(token: String): Boolean {
            return try {
                JWT.require(Algorithm.HMAC512(secret)).build().verify(token)
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 获取用户名
         *
         * @param token 令牌
         * @return 用户名
         */
        fun getUserName(token: String): String {
            return JWT.decode(token).getClaim("username").asString()
        }

        /**
         * 获取密码
         *
         * @param token 令牌
         * @return 密码
         */
        fun getPassword(token: String): String {
            return JWT.decode(token).getClaim("password").asString()
        }

        /**
         * 刷新token
         *
         * @param token 原token
         * @return 新token
         */
        fun refresh(token: String): String {
            var createDate = Date()
            var expireDate = Date(System.currentTimeMillis() + expireTime)

            val username = getUserName(token)
            val password = getPassword(token)
            var header = mapOf("username" to "username", "password" to password)
            return JWT.create()
                    .withHeader(header)
                    .withIssuedAt(createDate)
                    .withExpiresAt(expireDate)
                    .withClaim("username", username)
                    .withClaim("password", password)
                    .sign(Algorithm.HMAC512(secret))
        }
    }
}
