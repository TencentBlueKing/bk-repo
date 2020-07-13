package com.tencent.bkrepo.auth.util

import java.security.MessageDigest

object DataDigestUtils {

    /**
     * md5加密字符串
     * md5使用后转成16进制变成32个字节
     */
    private const val HEXNUM = 0xFF
    fun md5FromStr(str: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val result = digest.digest(str.toByteArray())
        return toHex(result)
    }

    fun md5FromByteArray(byteArr: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        val result = digest.digest(byteArr)
        return toHex(result)
    }

    private fun toHex(byteArray: ByteArray): String {
        // 转成16进制后是32字节
        return with(StringBuilder()) {
            byteArray.forEach {
                val hex = it.toInt() and (HEXNUM)
                val hexStr = Integer.toHexString(hex)
                if (hexStr.length == 1) {
                    append("0").append(hexStr)
                } else {
                    append(hexStr)
                }
            }
            toString()
        }
    }

    fun sha1FromStr(str: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val result = digest.digest(str.toByteArray())
        return toHex(result)
    }

    fun sha1FromByteArray(byteArr: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val result = digest.digest(byteArr)
        return toHex(result)
    }

    fun sha256FromByteArray(byteArr: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val result = digest.digest(byteArr)
        return toHex(result)
    }
}
