
package com.tencent.bkrepo.repository.service.experience

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * HTTP请求工具类
 */
object HttpUtils {

    private val logger = LoggerFactory.getLogger(HttpUtils::class.java)

    /**
     * 执行HTTP请求
     */
    fun doRequest(
        okHttpClient: OkHttpClient,
        request: Request,
        retry: Int = 0,
        retryDelayMs: Long = 500
    ): String {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                return when {
                    // 对于需要重试的状态码，如果还有重试次数就重试
                    code in setOf(502, 503, 504) && retry > 0 -> {
                        logger.warn("HTTP $code received, retrying, remaining retries: $retry, url=${request.url}")
                        Thread.sleep(retryDelayMs)
                        doRequest(okHttpClient, request, retry - 1, retryDelayMs)
                    }
                    // 其他所有情况都返回响应体
                    else -> body
                }
            }
        } catch (e: Exception) {
            return when {
                shouldRetry(e) && retry > 0 -> {
                    logger.warn("Request failed, retrying, remaining retries: $retry, " +
                                    "url=${request.url}, cause: ${e.message}")
                    Thread.sleep(retryDelayMs)
                    doRequest(okHttpClient, request, retry - 1, retryDelayMs)
                }
                else -> {
                    // 详细记录异常信息
                    logger.error("HTTP request failed after all retries, url=${request.url}, " +
                                     "exception=${e.javaClass.simpleName}, message=${e.message}", e)
                    throw ErrorCodeException(
                        status = HttpStatus.INTERNAL_SERVER_ERROR,
                        messageCode = RepositoryMessageCode.APP_EXPERIENCE_REQUEST_ERROR,
                        params = arrayOf("Request failed: ${e.message ?: "Unknown error"}")
                    )
                }
            }
        }
    }

    private fun shouldRetry(e: Exception): Boolean {
        return e is ConnectException || e is SocketTimeoutException
    }
}
