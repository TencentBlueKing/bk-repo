package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.exception.RpmIndexNotFoundException
import com.tencent.bkrepo.rpm.pojo.RpmPackagePojo
import java.util.regex.Pattern

object RpmStringUtils {

    fun String.toRpmPackagePojo(): RpmPackagePojo {
        val path = this.substringBeforeLast("/")
        val rpmArtifactName = this.substringAfterLast("/")
        val regex =
            """^(.+)-([0-9a-zA-Z\.]+)-([0-9a-zA-Z\.]+)\.(x86_64|i386|i586|i686|noarch])\.rpm$"""
        val matcher = Pattern.compile(regex).matcher(rpmArtifactName)
        if (matcher.find()) {
            return RpmPackagePojo(
                path = path,
                name = matcher.group(1),
                version = "${matcher.group(2)}-${matcher.group(3)}.${matcher.group(4)}"
            )
        } else {
            throw RpmIndexNotFoundException("Rpm artifact name can not resolve")
        }
    }
}
