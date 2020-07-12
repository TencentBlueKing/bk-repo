package com.tencent.bkrepo.common.api.constant

import java.util.UUID

object StringPool {
    const val EMPTY = ""
    const val DOT = "."
    const val SLASH = "/"
    const val COLON = ":"
    const val DASH = "-"
    const val HTTP = "http://"
    const val HTTPS = "https://"
    const val UNKNOWN = "Unknown"
    const val TEMP = "temp"

    const val MEDIA_TYPE_STREAM = "application/octet-stream"
    const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
    const val MEDIA_TYPE_HTML = "text/html; charset=UTF-8"

    const val ROOT = SLASH

    private val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun randomString(size: Int) = List(size) { alphabet.random() }.joinToString("")

    fun uniqueId() = UUID.randomUUID().toString().replace(DASH, EMPTY).toLowerCase()
}
