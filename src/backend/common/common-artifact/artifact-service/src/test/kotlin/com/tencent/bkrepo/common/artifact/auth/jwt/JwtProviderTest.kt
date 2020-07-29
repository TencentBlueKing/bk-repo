package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.AuthProperties
import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

internal class JwtProviderTest {

    @Test
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
    fun testJwtTokenExpires() {
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
}
