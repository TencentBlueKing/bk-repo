package com.tencent.bkrepo.common.storage.innercos.http

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object CosHttpClient {
    private val logger = LoggerFactory.getLogger(CosHttpClient::class.java)

    private val client = OkHttpClient().newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun <T> execute(request: Request, handler: HttpResponseHandler<T>): T {
        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            val message = buildMessage(request)
            throw RuntimeException("Failed to execute http request: $message", exception)
        }

        response.useOnCondition(!handler.keepConnection()) {
            try {
                if (it.isSuccessful) {
                    return handler.handle(it)
                } else {
                    if (it.code() == 404) {
                        val handle404Result = handler.handle404()
                        if (handle404Result != null) {
                            return handle404Result
                        }
                    }
                    throw RuntimeException("Response status error")
                }
            } catch (exception: Exception) {
                val message = buildMessage(request, it)
                throw RuntimeException("Failed to execute http request: $message", exception)
            }
        }
    }

    private fun buildMessage(request: Request, response: Response? = null): String {
        val requestTitle = "${request.method()} ${request.url()} ${response?.protocol()}"

        val builder = StringBuilder()
            .append(">>>> ")
            .appendln(requestTitle)
            .appendln(request.headers())

        if (response != null) {
            builder.append("<<<< ")
                .append(requestTitle)
                .append(response.code())
                .appendln("[${response.message()}]")
                .appendln(response.headers())
                .appendln(response.body()?.bytes()?.toString(Charset.forName("GB2312")))
        }
        val message = builder.toString()
        logger.warn(message)
        return message
    }
}
