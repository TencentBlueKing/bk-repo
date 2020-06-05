package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.exception.RpmRequestParamMissException
import com.tencent.bkrepo.rpm.util.UrlUtil.uriPattern
import java.util.regex.Pattern

object UrlUtil {
    fun String.uriPattern(): Map<String, String> {
        val regex = "^/([0-9a-zA-Z._\\-]+)/([0-9a-zA-Z._\\-]+)/([0-9a-zA-Z._\\-]+)/([0-9a-zA-Z._\\-]+rpm)$"
        val matcher = Pattern.compile(regex).matcher(this)
        try {
            while (matcher.find()) {
                return mapOf<String, String>(
                        // linux 版本
                        "releasever" to matcher.group(1),
                        "type" to matcher.group(2),
                        // cpu架构
                        "basearch" to matcher.group(3),
                        "artifactName" to matcher.group(4)
                )
            }
        } catch (e: Exception) {
            throw RpmRequestParamMissException("Miss necessary request param")
        }
        return mapOf()
    }
}

fun main() {
//    val str = "/7/os/x86_64/"
    val str = "/7/os/x86_64/hello-world-1-1.x86_64.rpm"
    val map = str.uriPattern()
    val a = "s"
}
