package com.tencent.bkrepo.dockeradapter.util

import com.tencent.bkrepo.dockeradapter.client.BkEsbClient
import com.tencent.bkrepo.dockeradapter.pojo.JwtData
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.provider.JCERSAPublicKey
import org.bouncycastle.openssl.PEMReader
import org.bouncycastle.openssl.PasswordFinder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.Security

@Component
class JwtUtils @Autowired constructor(
    private val bkEsbClient: BkEsbClient
) {
    var jwtKey: String? = null

    fun parseJwtToken(jwtToken: String): JwtData? {
        val jwtKey = getHarborApiKey()
        Security.addProvider(BouncyCastleProvider())
        val bais = ByteArrayInputStream(jwtKey.toByteArray())
        val reader = PEMReader(InputStreamReader(bais), PasswordFinder { "".toCharArray() })
        return try {
            val keyPair = reader.readObject() as JCERSAPublicKey
            val jwtParser = Jwts.parser().setSigningKey(keyPair)
            val claims = jwtParser.parse(jwtToken).body as Claims
            val user = claims["user"] as Map<*, *>
            val userName = user["username"] as String
            JwtData(userName)
        } catch (ex: Exception) {
            logger.warn("parse jwt token failed", ex)
            null
        }
    }

    fun getHarborApiKey(): String {
        if (!jwtKey.isNullOrBlank()) {
            return jwtKey!!
        }

        try {
            val paasResponse = bkEsbClient.getHarborApiKeyFromApigw()
            if (paasResponse.data == null) {
                throw RuntimeException("get api key from apigw failed")
            }
            jwtKey = paasResponse.data.publicKey
            return jwtKey!!
        } catch (e: Exception) {
            logger.error("get api key from apigw failed", e.message)
            throw RuntimeException("get api key from apigw failed")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JwtUtils::class.java)
    }
}