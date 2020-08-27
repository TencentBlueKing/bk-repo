package com.tencent.bkrepo.npm.utils

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OkHttpUtil {

    private val okHttpClient: OkHttpClient by lazy {
        HttpClientBuilderFactory.create().readTimeout(TIMEOUT, TimeUnit.SECONDS).build()
    }

    @Retryable(Exception::class, maxAttempts = 3, backoff = Backoff(delay = 5 * 1000, multiplier = 1.0))
    fun doGet(url: String, headers: Map<String, String> = emptyMap()): Response {
        val requestBuilder = Request.Builder().url(url).get()
        if (headers.isNotEmpty()) {
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
        }
        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()
        if (!checkResponse(response)) {
            throw Exception("request http url: [$url], headers:[$headers] failed")
        }
        return response
    }

    @Recover
    fun recover(exception: Exception, url: String, headers: Map<String, String> = emptyMap()): Response {
        logger.error("recover, retry http send url: [$url], headers:[$headers] failed, exception: $exception")
        throw ArtifactNotFoundException("http send url: [$url] failed.")
    }

    /**
     * 检查下载响应
     */
    private fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download file from remote failed: [${response.code()}]")
            return false
        }
        return true
    }

    companion object {
        const val TIMEOUT = 5 * 60L
        val logger: Logger = LoggerFactory.getLogger(OkHttpUtil::class.java)
    }
}
