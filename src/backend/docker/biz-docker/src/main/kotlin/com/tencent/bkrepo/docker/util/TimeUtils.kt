package com.tencent.bkrepo.docker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

object TimeUtils {
    fun getGMTTime(): String {
        val timeZone = TimeZone.getTimeZone("GMT")
        val time = System.currentTimeMillis()
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        simpleDateFormat.timeZone = timeZone
        val date = Date(time)
        return simpleDateFormat.format(date)
    }
}
