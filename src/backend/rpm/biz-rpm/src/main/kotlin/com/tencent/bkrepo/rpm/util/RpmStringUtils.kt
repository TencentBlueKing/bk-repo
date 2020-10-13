package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.exception.RpmIndexNotFoundException
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import java.util.regex.Pattern

object RpmStringUtils {

    fun String.toRpmVersion(): RpmVersion {
        val rpmArtifactName = this.substringAfterLast("/")
        val regex =
                """^(.+)-([0-9a-zA-Z\.]+)-([0-9a-zA-Z\.]+)\.([0-9a-zA-Z_]+)\.(rpm|xml)$"""
        val matcher = Pattern.compile(regex).matcher(rpmArtifactName)
        if (matcher.find()) {
            return RpmVersion(
                    name = matcher.group(1),
                    arch = matcher.group(4),
                    epoch = "0",
                    ver = matcher.group(2),
                    rel = matcher.group(3)
            )
        } else {
            throw RpmIndexNotFoundException("Rpm artifact name can not resolve")
        }
    }
}