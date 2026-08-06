package com.tencent.bkrepo.repository.client

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.repository.config.IlnetProperties
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficRequest
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficResponse
import io.micrometer.observation.ObservationRegistry
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class IlnetLinkTrafficClient(
    private val properties: IlnetProperties,
    private val registry: ObservationRegistry,
) {
    private val okHttpClient = HttpClientBuilderFactory.create(registry = registry).build()

    fun queryTraffic(request: LinkTrafficRequest): LinkTrafficResponse {
        val url = buildUrl(properties.trafficPath)
        val body = request.toJsonString()
        val httpRequest = IlnetRioAuthHelper.applyAuth(
            Request.Builder().url(url),
            properties.paasid,
            properties.token,
        )
            .method("POST", body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeWithRetry(httpRequest, "queryTraffic")
    }

    fun health() {
        val url = buildUrl(properties.healthPath)
        val httpRequest = IlnetRioAuthHelper.applyAuth(
            Request.Builder().url(url),
            properties.paasid,
            properties.token,
        )
            .method("GET", null)
            .build()
        executeHealthWithRetry(httpRequest)
    }

    private fun buildUrl(path: String): String {
        val base = properties.server.trim().trimEnd('/')
        val normalizedBase = if (base.startsWith("http://", ignoreCase = true)
            || base.startsWith("https://", ignoreCase = true)
        ) {
            base
        } else {
            "http://$base"
        }
        val normalizedPath = path.trim().trimStart('/')
        return "$normalizedBase/$normalizedPath"
    }

    private fun executeWithRetry(request: Request, operationName: String): LinkTrafficResponse {
        var lastException: Exception? = null
        val maxAttempts = properties.retryCount.coerceAtLeast(0) + 1
        repeat(maxAttempts) { attempt ->
            try {
                return execute(request, operationName)
            } catch (e: Exception) {
                lastException = e
                val retryable = e is IlnetServerException
                if (!retryable || attempt == maxAttempts - 1) {
                    throw e
                }
                logger.warn("$operationName failed on attempt ${attempt + 1}, retrying...", e)
            }
        }
        throw lastException ?: IllegalStateException("$operationName failed")
    }

    private fun executeHealthWithRetry(request: Request) {
        var lastException: Exception? = null
        val maxAttempts = properties.retryCount.coerceAtLeast(0) + 1
        repeat(maxAttempts) { attempt ->
            try {
                executeHealth(request)
                return
            } catch (e: Exception) {
                lastException = e
                val retryable = e is IlnetServerException
                if (!retryable || attempt == maxAttempts - 1) {
                    throw e
                }
                logger.warn("health failed on attempt ${attempt + 1}, retrying...", e)
            }
        }
        throw lastException ?: IllegalStateException("health failed")
    }

    private fun executeHealth(request: Request) {
        logger.info("health, method=[${request.method}], requestUrl=[${request.url}]")
        try {
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.close()
                when {
                    response.code == 200 -> return
                    response.code >= 500 -> {
                        logger.warn("ilnet health check failed, httpCode=${response.code}")
                        throw IlnetServerException()
                    }
                    else -> {
                        logger.warn("ilnet health check failed, httpCode=${response.code}")
                        throw IlnetClientException()
                    }
                }
            }
        } catch (e: IOException) {
            logger.warn("health failed, method=[${request.method}], requestUrl=[${request.url}]", e)
            throw IlnetClientException(e)
        }
    }

    private fun execute(request: Request, operationName: String): LinkTrafficResponse {
        logger.info("$operationName, method=[${request.method}], requestUrl=[${request.url}]")
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    logger.warn("Empty response from ilnet, httpCode=${response.code}")
                    throw IlnetClientException()
                }
                if (response.code >= 500) {
                    logger.warn("ilnet server error, httpCode=${response.code}, body=$body")
                    throw IlnetServerException()
                }
                return body.readJsonString<LinkTrafficResponse>()
            }
        } catch (e: IOException) {
            logger.warn("$operationName failed, method=[${request.method}], requestUrl=[${request.url}]", e)
            throw IlnetClientException(e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IlnetLinkTrafficClient::class.java)
        private val JSON_MEDIA_TYPE = MediaTypes.APPLICATION_JSON.toMediaTypeOrNull()
    }
}

class IlnetClientException(cause: Throwable? = null) :
    ErrorCodeException(RepositoryMessageCode.ILNET_SERVICE_UNAVAILABLE) {
    init {
        cause?.let { initCause(it) }
    }
}

class IlnetServerException :
    ErrorCodeException(RepositoryMessageCode.ILNET_SERVICE_UNAVAILABLE)
