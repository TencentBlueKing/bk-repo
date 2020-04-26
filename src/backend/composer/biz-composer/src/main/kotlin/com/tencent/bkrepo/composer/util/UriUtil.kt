package com.tencent.bkrepo.composer.util

import java.util.regex.Pattern
import kotlin.collections.HashMap

object UriUtil {
    fun getUriArgs(uri: String): HashMap<String, String>? {
        val regex = "^/([a-zA-Z0-9]+)-([\\d.]+?).(tar|zip|tar.gz|tgz)$"
        val matcher = Pattern.compile(regex).matcher(uri)
        while (matcher.find()) {
            return hashMapOf("filename" to matcher.group(1),
            "version" to matcher.group(2),
            "format" to matcher.group(3))
        }
        return null
    }
}


