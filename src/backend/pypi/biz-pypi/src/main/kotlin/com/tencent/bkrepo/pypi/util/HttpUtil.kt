package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import okhttp3.Request
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object HttpUtil {

    fun String.downloadUrl(): InputStream? {
        val okHttp = HttpClientBuilderFactory.create().build()
        val request = Request.Builder().get().url(this).build()
        val response = okHttp.newCall(request).execute()
        val inputStream = response.body()?.byteStream()
        return inputStream
    }

    fun String.downloadUrlHttpClient(): InputStream? {
        val url = URL(this)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return connection.inputStream
    }
}
