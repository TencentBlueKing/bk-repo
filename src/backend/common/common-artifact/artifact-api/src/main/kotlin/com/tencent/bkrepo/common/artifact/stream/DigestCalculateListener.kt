package com.tencent.bkrepo.common.artifact.stream

import java.math.BigInteger
import java.security.MessageDigest

class DigestCalculateListener : StreamReceiveListener {
    private val md5Digest = MessageDigest.getInstance("MD5")
    private val sha256Digest = MessageDigest.getInstance("SHA-256")

    lateinit var md5: String
    lateinit var sha256: String

    override fun data(buffer: ByteArray, offset: Int, length: Int) {
        md5Digest.update(buffer, offset, length)
        sha256Digest.update(buffer, offset, length)
    }

    override fun finished() {
        md5 = hexToString(md5Digest.digest(), 32)
        sha256 = hexToString(sha256Digest.digest(), 64)
    }

    private fun hexToString(byteArray: ByteArray, length: Int): String {
        val hashInt = BigInteger(1, byteArray)
        val hashText = hashInt.toString(16)
        return if (hashText.length < length) "0".repeat(length - hashText.length) + hashText else hashText
    }
}
