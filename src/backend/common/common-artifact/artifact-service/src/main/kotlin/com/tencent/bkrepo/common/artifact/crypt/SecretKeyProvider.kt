package com.tencent.bkrepo.common.artifact.crypt

import java.nio.charset.StandardCharsets
import java.util.Base64

class SecretKeyProvider(serverKey: String) {
    init {
        Companion.serverKey = serverKey
    }

    companion object {
        private lateinit var serverKey: String
        fun getKeyBytes(key: String): ByteArray {
            val k1Bytes = Base64.getDecoder().decode(key)
            val half = k1Bytes.size / 2
            val high = k1Bytes.copyOfRange(0, half)
            val low = k1Bytes.copyOfRange(half, k1Bytes.size)
            val k2Bytes = serverKey.toByteArray(StandardCharsets.UTF_8)
            return and(and(high, low), k2Bytes)
        }

        private fun and(b1: ByteArray, b2: ByteArray): ByteArray {
            val len = b1.size.coerceAtMost(b2.size)
            val maxLen = b1.size.coerceAtLeast(b2.size)
            val b3 = ByteArray(maxLen)
            for (i in 0 until len) {
                b3[i] = (b1[i].toInt() and b2[i].toInt()).toByte()
            }
            if (b1.size > len) {
                b1.copyInto(b3, len, len, b1.size)
            }
            if (b2.size > len) {
                b2.copyInto(b3, len, len, b2.size)
            }
            return b3
        }
    }
}