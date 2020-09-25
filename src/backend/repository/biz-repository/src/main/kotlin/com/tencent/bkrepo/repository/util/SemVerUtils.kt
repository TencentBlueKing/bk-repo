package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.constant.CharPool

/**
 * 语义化版本工具类
 */
object SemVerUtils {
    fun ordinal(version: String): Long {
        var ordinal: Long = 0L
        val parts = version.split(CharPool.DOT)
        parts.forEach { part ->
            val number = try {
                Integer.valueOf(part)
            } catch (e: Exception) {
                999
            }
            if (number > 0) {
                ordinal = ordinal * 1000 + number
            }
        }
        return ordinal
    }
}

fun main() {
    println(SemVerUtils.ordinal("1.0.0"))
    println(SemVerUtils.ordinal("1.0.1"))
}