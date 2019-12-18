package com.tencent.bkrepo.maven.utils

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    fun getGMTTime(): String {
        val timeZone = TimeZone.getTimeZone("GMT")
        val time = System.currentTimeMillis()
        val simpleDateFormat = SimpleDateFormat("yyyyMMddHHmmss")
        simpleDateFormat.timeZone = timeZone
        val date = Date(time)
        return simpleDateFormat.format(date)

    }
}