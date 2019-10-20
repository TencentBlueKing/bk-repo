package com.tencent.bkrepo.generic.util

object PathUtils {
    fun toFullPath(path: String): String {
        return if (path.isNullOrBlank()) {
            "/"
        } else if (path.startsWith("/")) {
            path
        } else {
            "/$path"
        }
    }
}