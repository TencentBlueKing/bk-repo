package com.tencent.bkrepo.common.artifact.auth.jwt

import com.tencent.bkrepo.common.artifact.auth.AuthProperties
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class JwtProviderTest {

    private val authProperties = AuthProperties()

    @Test
    fun testJwtProvider() {
        JwtProvider(authProperties)
        val claims = mutableMapOf<String, Any>("key" to "value")
        val token = JwtProvider.generateToken("Taylor", claims)
        val jws = JwtProvider.validateToken(token)
        Assertions.assertEquals("value", jws.body["key"])
        Assertions.assertEquals("Taylor", jws.body.subject)
    }
}
