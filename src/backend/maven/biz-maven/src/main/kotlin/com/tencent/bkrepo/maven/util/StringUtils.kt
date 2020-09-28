package com.tencent.bkrepo.maven.util

import org.apache.commons.lang.StringUtils

object StringUtils {

    fun String.formatSeparator(oldSeparator: String, newSeparator: String): String {
        val strList = this.removePrefix(oldSeparator).removeSuffix(oldSeparator).split(oldSeparator)
        return StringUtils.join(strList, newSeparator)
    }
}
