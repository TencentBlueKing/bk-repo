package com.tencent.bkrepo.common.api.util

object MaskPartStringUtil {
    private const val SHOW_LENGTH = 3

    fun maskPartString(str: String): String {
        return if (str.length < SHOW_LENGTH * 4) {
            "******"
        } else {
            val prefix = str.substring(0, SHOW_LENGTH)
            val suffix = str.substring(str.length - SHOW_LENGTH, str.length)
            "$prefix***$suffix"
        }
    }

}