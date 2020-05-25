package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.exception.PypiUnSupportCompressException
import java.lang.Exception
import java.util.regex.Pattern

object FileNameUtil {
    fun String.fileFormat(): String? {
        try {
            val regex = "^(.+).(tar|zip|tar.gz|tgz|whl)$"
            val matcher = Pattern.compile(regex).matcher(this)
            while (matcher.find()) {
                return matcher.group(2)
            }
        } catch (e: Exception) {
            throw PypiUnSupportCompressException("Can not support compress format!")
        }
        return null
    }
}
