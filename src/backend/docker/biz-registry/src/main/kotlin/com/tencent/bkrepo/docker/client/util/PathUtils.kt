package com.tencent.bkrepo.docker.client.util

import java.util.regex.Pattern

object PathUtils {

    val ARCHIVE_SEP = '!'
    val ALL_PATH_VALUE = "**"
    private val PATTERN_SLASHES = Pattern.compile("/+")

    fun hasLength(str: String?): Boolean {
        return str != null && str.length > 0
    }

    fun formatPath(path: String): String {
        var path = path
        if (hasText(path)) {
            path = path.replace('\\', '/')
            return normalizeSlashes(path).toString()
        } else {
            return ""
        }
    }

    fun hasText(str: String): Boolean {
        if (!hasLength(str)) {
            return false
        } else {
            val strLen = str.length

            for (i in 0 until strLen) {
                if (!Character.isWhitespace(str[i])) {
                    return true
                }
            }

            return false
        }
    }

    fun normalizeSlashes(path: CharSequence?): CharSequence? {
        return if (path == null) null else PATTERN_SLASHES.matcher(path).replaceAll("/")
    }

    fun trimLeadingSlashes(path: CharSequence): String? {
        val res = trimLeadingSlashChars(path)
        return if (res != null) res!!.toString() else null
    }

    fun trimLeadingSlashChars(path: CharSequence?): CharSequence? {
        var path = path
        if (path == null) {
            return null
        } else if (path.length > 0 && path[0] == '/') {
            path = path.subSequence(1, path.length)
            return trimLeadingSlashChars(path)
        } else {
            return path
        }
    }
}
