package com.tencent.bkrepo.migrate.util

import kotlin.random.Random

object ShortUUIDUtils {
    fun shortUUID(): String {
        val stringBuilder = StringBuilder()
        for (i in 1..5) {
            stringBuilder.append(Random.nextInt(9))
        }
        return stringBuilder.toString()
    }
}
