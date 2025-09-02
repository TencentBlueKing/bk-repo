
package com.tencent.bkrepo.repository.service.experience

import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpServerErrorException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException
import java.lang.RuntimeException

object HttpUtils {

    private val logger = LoggerFactory.getLogger(HttpUtils::class.java)

    /**
     * 执行HTTP请求
     */
    fun doRequest(
        okHttpClient: OkHttpClient,
        request: Request,
        retry: Int = 0,
        acceptCode: Set<Int> = emptySet(),
        retryDelayMs: Long = 500
    ): String {
        var currentRetry = retry
        while (true) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string() ?: ""

                    // 成功响应或可接受的状态码直接返回
                    if (response.isSuccessful || acceptCode.contains(code)) {
                        return body
                    }

                    // 对于5xx服务器错误，HttpServerErrorException
                    if (code in 500..599) {
                        throw HttpServerErrorException(
                            HttpStatusCode.valueOf(code),
                            "Server error: HTTP $code, body: $body"
                        )
                    }

                    // 对于4xx等其他错误，抛出RuntimeException，不重试
                    throw RuntimeException(
                        "HTTP request failed, url=${request.url}, code=$code, body=$body"
                    )
                }
            } catch (e: Exception) {
                // 判断是否应该重试
                val shouldRetry = isRetryableException(e)

                if (shouldRetry && currentRetry > 0) {
                    logger.warn("HTTP request failed, retrying ${retry - currentRetry + 1}/$retry, " +
                                    "url=${request.url}, cause: ${e.message}")
                    Thread.sleep(retryDelayMs)
                    currentRetry--
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * 判断异常是否可以重试
     * 只有以下情况才重试：
     * 1. 网络连接异常（ConnectException, UnknownHostException）
     * 2. 读写超时异常（SocketTimeoutException）
     * 3. 服务器5xx错误（HttpServerErrorException）
     * 4. 其他IO异常（如网络中断等）
     */
    private fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            is ConnectException -> true              // 连接失败
            is UnknownHostException -> true          // DNS解析失败
            is SocketTimeoutException -> true        // 读写超时
            is HttpServerErrorException -> true      // 服务器5xx错误
            is IOException -> true                   // 其他IO异常
            else -> false                           // 其他异常不重试
        }
    }
}
