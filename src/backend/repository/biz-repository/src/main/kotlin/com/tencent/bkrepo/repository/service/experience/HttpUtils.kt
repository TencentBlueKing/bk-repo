package com.tencent.bkrepo.repository.service.experience

import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

object HttpUtils {

    private val logger = LoggerFactory.getLogger(HttpUtils::class.java)

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
                    if (response.isSuccessful || acceptCode.contains(code)) {
                        return body
                    }
                    throw RuntimeException(
                        "HTTP request failed, url=${request.url}, code=$code, body=$body"
                    )
                }
            } catch (e: Exception) {
                if (currentRetry > 0) {
                    logger.warn("HTTP request failed, retrying ${retry - currentRetry + 1}/$retry, url=${request.url}, cause: ${e.message}")
                    Thread.sleep(retryDelayMs)
                    currentRetry--
                } else {
                    logger.error("HTTP request finally failed, url=${request.url}", e)
                    throw RuntimeException("HTTP request error, url=${request.url}", e)
                }
            }
        }
    }
}
