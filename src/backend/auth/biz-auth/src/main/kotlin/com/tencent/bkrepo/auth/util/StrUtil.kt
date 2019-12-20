package com.tencent.bkrepo.auth.util

object StrUtil {

    /**
     * 生成随机字符串
     */

    fun generateNonce(size: Int): String {
        val nonceScope = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val scopeSize = nonceScope.length
        val nonceItem: (Int) -> Char = { nonceScope[(scopeSize * Math.random()).toInt()] }
        return Array(size, nonceItem).joinToString("")
    }
}