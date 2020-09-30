package com.tencent.bkrepo.rpm.util

import org.apache.commons.lang.StringUtils

object StrUtils {
    fun String.formatSeparator(oldSeparator: String, newSeparator: String): String {
        val strList = this.removePrefix(oldSeparator).removeSuffix(oldSeparator).split(oldSeparator)
        return StringUtils.join(strList, newSeparator)
    }
}
