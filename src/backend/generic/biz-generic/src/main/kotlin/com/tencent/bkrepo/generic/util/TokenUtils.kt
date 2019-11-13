package com.tencent.bkrepo.generic.util

import java.util.*

object TokenUtils {
    fun createToken(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
}