package com.tencent.bkrepo.npm.utils

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    fun getTime(dateTime: LocalDateTime): String {
        val timeZone = TimeZone.getTimeZone("GMT")
        val time = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        simpleDateFormat.timeZone = timeZone
        val date = Date(time)
        return simpleDateFormat.format(date)
    }

    fun compareTime(oldTime: String, newTime: String): Boolean {
        val oldLocalDateTime = LocalDateTime.parse(oldTime, DateTimeFormatter.ISO_DATE_TIME)
        val newLocalDateTime = LocalDateTime.parse(newTime, DateTimeFormatter.ISO_DATE_TIME)
        return oldLocalDateTime.isAfter(newLocalDateTime)
    }
}
