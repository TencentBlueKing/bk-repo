package com.tencent.bkrepo.registry.repomd.util

import java.io.File
import java.util.regex.Pattern

object PathUtils {
    private val PATTERN_SLASHES = Pattern.compile("/+")

    fun getParent(path: String?): String? {
        if (path == null) {
            return null
        } else {
            val dummy = File(path)
            return formatPath(dummy.parent)
        }
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

    fun hasLength(str: String?): Boolean {
        return str != null && str.length > 0
    }

    fun normalizeSlashes(path: CharSequence?): CharSequence? {
        return if (path == null) null else PATTERN_SLASHES.matcher(path).replaceAll("/")
    }

    fun getFileName(path: String?): String? {
        if (path == null) {
            return null
        } else {
            val dummy = File(path)
            return dummy.name
        }
    }
}
