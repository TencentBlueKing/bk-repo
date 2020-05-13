package com.tencent.bkrepo.pypi.util

import java.util.regex.Pattern

object FileNameUtil {
    fun String.fileFormat(): String? {
        val regex = "^(.+).(tar|zip|tar.gz|tgz|whl)$"
        val matcher = Pattern.compile(regex).matcher(this)
        while (matcher.find()) {
            return matcher.group(2)
        }
        return null
    }
}
