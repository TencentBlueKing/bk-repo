package com.tencent.bkrepo.dockeradapter.util

import java.util.Random

object AccountUtils {
    private val BASE = "abcdefghijklmnopqrstuvwxyz"

    fun generateRandomPassword(length: Int): String {
        val random = Random()
        val sb = StringBuilder()
        for (i in 0..length) {
            sb.append(BASE[random.nextInt(BASE.length)])
        }
        return "0H$sb"
    }
}