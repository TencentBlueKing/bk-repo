package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.pojo.RpmVersion
import org.apache.commons.lang.StringUtils

object RpmStringUtils {

    fun String.formatSeparator(oldSeparator: String, newSeparator: String): String {
        val strList = this.removePrefix(oldSeparator).removeSuffix(oldSeparator).split(oldSeparator)
        return StringUtils.join(strList, newSeparator)
    }

    fun RpmVersion.getVersion(): String {
        return StringBuilder(this.epoch).append("-")
                .append(this.ver).append("-")
                .append(this.rel).append(".").
                append(this.arch).toString()
    }

    fun String.getVersionWithoutEpoch()

}