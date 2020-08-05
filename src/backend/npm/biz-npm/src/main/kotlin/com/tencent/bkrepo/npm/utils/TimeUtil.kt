package com.tencent.bkrepo.npm.utils

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

object TimeUtil {
    fun getGMTTime(): String {
        val ldt = LocalDateTime.now(ZoneId.of("GMT"))
        val atZone = ldt.atZone(ZoneOffset.UTC)
        return atZone.toString()
    }

    fun getGMTTime(dateTime: LocalDateTime): String {
        val timeZone = TimeZone.getTimeZone("GMT")
        val time = dateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        simpleDateFormat.timeZone = timeZone
        val date = Date(time)
        return simpleDateFormat.format(date)
    }
}