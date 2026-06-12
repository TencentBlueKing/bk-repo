package com.tencent.bkrepo.repository.client

import okhttp3.Request
import java.security.MessageDigest
import java.util.UUID

object IlnetRioAuthHelper {
    private const val HEADER_PAASID = "x-rio-paasid"
    private const val HEADER_SIGNATURE = "x-rio-signature"
    private const val HEADER_TIMESTAMP = "x-rio-timestamp"
    private const val HEADER_NONCE = "x-rio-nonce"

    fun sign(timestamp: String, token: String, nonce: String): String {
        val raw = timestamp + token + nonce + timestamp
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.uppercase()
    }

    fun applyAuth(builder: Request.Builder, paasid: String, token: String): Request.Builder {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val signature = sign(timestamp, token, nonce)
        return builder
            .addHeader(HEADER_PAASID, paasid)
            .addHeader(HEADER_SIGNATURE, signature)
            .addHeader(HEADER_TIMESTAMP, timestamp)
            .addHeader(HEADER_NONCE, nonce)
    }
}
