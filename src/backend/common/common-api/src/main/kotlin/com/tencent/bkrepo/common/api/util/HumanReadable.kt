package com.tencent.bkrepo.common.api.util

import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object HumanReadable {

    private val sizeUnits = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    private val sizeFormat = DecimalFormat("#,##0.#").apply { maximumFractionDigits = 2 }

    fun size(bytes: Long): String {
        var size = bytes.toDouble()
        var index = 0
        while (size >= 1024) {
            size /= 1024.0
            index += 1
        }
        return "${sizeFormat.format(size)} ${sizeUnits[index]}"
    }

    fun throughput(bytes: Long, nano: Long): String {
        val speed = bytes / nano * 1000 * 1000 * 1000
        return size(speed) + "/s"
    }

    fun time(nano: Long): String {
        val unit = chooseUnit(nano)
        val value = nano.toDouble() / TimeUnit.NANOSECONDS.convert(1, unit)
        return String.format(Locale.ROOT, "%.4g", value) + " " + abbreviate(unit)
    }

    fun time(time: Long, unit: TimeUnit): String {
        return time(unit.toNanos(time))
    }

    private fun chooseUnit(nano: Long): TimeUnit {
        if (TimeUnit.DAYS.convert(nano, TimeUnit.NANOSECONDS) > 0) return TimeUnit.DAYS
        if (TimeUnit.HOURS.convert(nano, TimeUnit.NANOSECONDS) > 0) return TimeUnit.HOURS
        if (TimeUnit.MINUTES.convert(nano, TimeUnit.NANOSECONDS) > 0) return TimeUnit.MINUTES
        if (TimeUnit.SECONDS.convert(nano, TimeUnit.NANOSECONDS) > 0) return TimeUnit.SECONDS
        if (TimeUnit.MILLISECONDS.convert(nano, TimeUnit.NANOSECONDS) > 0) return TimeUnit.MILLISECONDS
        if (TimeUnit.MICROSECONDS.convert(nano, TimeUnit.NANOSECONDS) > 0) return TimeUnit.MICROSECONDS
        return TimeUnit.NANOSECONDS
    }

    private fun abbreviate(unit: TimeUnit): String {
        return when (unit) {
            TimeUnit.NANOSECONDS -> "ns"
            TimeUnit.MICROSECONDS -> "μs"
            TimeUnit.MILLISECONDS -> "ms"
            TimeUnit.SECONDS -> "s"
            TimeUnit.MINUTES -> "min"
            TimeUnit.HOURS -> "h"
            TimeUnit.DAYS -> "d"
            else -> throw AssertionError()
        }
    }
}
