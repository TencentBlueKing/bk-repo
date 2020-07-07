package com.tencent.bkrepo.pypi.util

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object HttpUtil {

    fun String.downloadUrlHttpClient(): InputStream? {
        val url = URL(this)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return connection.inputStream
    }
}
