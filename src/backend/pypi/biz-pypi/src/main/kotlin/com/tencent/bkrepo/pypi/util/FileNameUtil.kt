package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.exception.PypiUnSupportCompressException
import java.util.regex.Pattern

object FileNameUtil {
    fun String.fileFormat(): String? {
        val regex = "^(.+).(tar|zip|tar.gz|tgz|whl)$"
        val matcher = Pattern.compile(regex).matcher(this)
        while (matcher.find()) {
            return matcher.group(2)
        }
        throw PypiUnSupportCompressException("Can not support compress format!")
    }
}
