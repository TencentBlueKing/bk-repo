package com.tencent.bkrepo.common.security.util

import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

@DisplayName("JwtUtils工具类测试")
internal class JwtUtilsTest {

    @Test
    @DisplayName("测试生成jwt token")
    fun testJwtUtils() {
        val expiration = Duration.ZERO
        val signingKey = JwtUtils.createSigningKey("key")
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtUtils.generateToken(signingKey, expiration, "Taylor", claims)
        val jws = JwtUtils.validateToken(signingKey, token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)
    }

    @Test
    @DisplayName("测试过期jwt auth token")
    fun testJwtTokenWithPositiveExpiration() {
        val expiration = Duration.ofSeconds(1)
        val signingKey = JwtUtils.createSigningKey("")
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtUtils.generateToken(signingKey, expiration, "Taylor", claims)
        val jws = JwtUtils.validateToken(signingKey, token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)

        Thread.sleep(2000)
        assertThrows<ExpiredJwtException> { JwtUtils.validateToken(signingKey, token) }
    }

    @Test
    @DisplayName("测试不过期jwt auth token")
    fun testJwtTokenWithZeroExpiration() {
        val expiration = Duration.ZERO
        val signingKey = JwtUtils.createSigningKey("")
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtUtils.generateToken(signingKey, expiration, "Taylor", claims)
        val jws = JwtUtils.validateToken(signingKey, token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)

        Thread.sleep(2000)
        JwtUtils.validateToken(signingKey, token)
    }

    @Test
    @DisplayName("测试不过期jwt auth token")
    fun testJwtTokenWithNegativeExpiration() {
        val expiration = Duration.ofSeconds(-1)
        val signingKey = JwtUtils.createSigningKey("")
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtUtils.generateToken(signingKey, expiration, "Taylor", claims)
        val jws = JwtUtils.validateToken(signingKey, token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)

        Thread.sleep(2000)
        JwtUtils.validateToken(signingKey, token)
    }

    @Test
    @DisplayName("测试不带subject")
    fun testJwtTokenWithoutSubject() {
        val expiration = Duration.ofSeconds(-1)
        val signingKey = JwtUtils.createSigningKey("")
        val token = JwtUtils.generateToken(signingKey, expiration)
        val jws = JwtUtils.validateToken(signingKey, token)
        Assertions.assertNull(jws.body.subject)
    }
}
