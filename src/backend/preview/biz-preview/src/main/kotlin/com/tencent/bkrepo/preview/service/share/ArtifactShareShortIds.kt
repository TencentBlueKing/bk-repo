package com.tencent.bkrepo.preview.service.share

import java.security.SecureRandom

/**
 * 浏览器短链码：8 位 Base62（`0-9A-Za-z`），大小写敏感。
 */
object ArtifactShareShortIds {

    const val LENGTH = 8

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val random = SecureRandom()

    fun isValid(value: String): Boolean {
        return value.length == LENGTH && value.all { it in ALPHABET }
    }

    fun generate(): String {
        val chars = CharArray(LENGTH)
        repeat(LENGTH) { index ->
            chars[index] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }
}
