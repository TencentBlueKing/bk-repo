package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.AuthProperties
import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

internal class JwtProviderTest {

    @Test
    @DisplayName("测试生成jwt token")
    fun testJwtProvider() {
        val authProperties = AuthProperties().apply {
            jwt.secretKey = "1"
        }
        JwtProvider(authProperties)
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtProvider.generateToken("Taylor", claims)
        val jws = JwtProvider.validateToken(token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)
    }

    @Test
    @DisplayName("测试过期jwt auth token")
    fun testJwtTokenWithPositiveExpiration() {
        val authProperties = AuthProperties().apply {
            jwt.secretKey = ""
            jwt.expiration = Duration.ofSeconds(2)
        }
        JwtProvider(authProperties)
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtProvider.generateToken("Taylor", claims)
        val jws = JwtProvider.validateToken(token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)

        Thread.sleep(2000)
        assertThrows<ExpiredJwtException> { JwtProvider.validateToken(token) }
    }

    @Test
    @DisplayName("测试不过期jwt auth token")
    fun testJwtTokenWithZeroExpiration() {
        val authProperties = AuthProperties().apply {
            jwt.secretKey = ""
            jwt.expiration = Duration.ofSeconds(0)
        }
        JwtProvider(authProperties)
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtProvider.generateToken("Taylor", claims)
        val jws = JwtProvider.validateToken(token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)

        Thread.sleep(2000)
        JwtProvider.validateToken(token)
    }

    @Test
    @DisplayName("测试不过期jwt auth token")
    fun testJwtTokenWithNegativeExpiration() {
        val authProperties = AuthProperties().apply {
            jwt.secretKey = ""
            jwt.expiration = Duration.ofSeconds(-1)
        }
        JwtProvider(authProperties)
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtProvider.generateToken("Taylor", claims)
        val jws = JwtProvider.validateToken(token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)

        Thread.sleep(2000)
        JwtProvider.validateToken(token)
    }
}
