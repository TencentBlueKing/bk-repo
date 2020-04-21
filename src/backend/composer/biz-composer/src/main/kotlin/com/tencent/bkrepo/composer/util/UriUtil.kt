package com.tencent.bkrepo.composer.util

import java.util.regex.Pattern

object UriUtil {
    fun getUriArgs(uri: String): HashMap<String, String>? {
        val regex = "^([a-zA-Z0-9]+)-([\\d.]+?).(tar|zip|tar.gz|tgz)$"
        val matcher = Pattern.compile(regex).matcher(uri)
        while (matcher.find()) {
            return hashMapOf("filename" to matcher.group(1),
            "version" to matcher.group(2),
            "format" to matcher.group(3))
        }
        return null
    }

    fun removeFileFormat(uri: String): String {
        return uri.removeSuffix(".tar.gz")
                .removeSuffix("zip")
                .removeSuffix("tar")
                .removeSuffix("tgz")
    }

    fun getJarPath(): String {
        return DecompressUtil::class.java.getResource("/").path.removeSuffix("/src/backend/composer/biz-composer/build/classes/kotlin/main/")
    }
}

//fun main() {
//    UriUtil.getUriArgs("monolog-2.0.2.tar.gz]")
//}

