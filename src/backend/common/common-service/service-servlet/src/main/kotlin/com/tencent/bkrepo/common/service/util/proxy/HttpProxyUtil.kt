package com.tencent.bkrepo.common.service.util.proxy

import com.tencent.bkrepo.common.api.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpProxyUtil(
    private val client: OkHttpClient = HttpClientBuilderFactory.create().build(),
    private val defaultProxyCallHandler: ProxyCallHandler = DefaultProxyCallHandler()
) {
    fun proxy(
        proxyRequest: HttpServletRequest,
        proxyResponse: HttpServletResponse,
        targetUrl: String,
        prefix: String? = null,
        proxyCallHandler: ProxyCallHandler = defaultProxyCallHandler,
    ) {
        val newUrl = if (proxyRequest.queryString.isNullOrEmpty()) {
            "$targetUrl${proxyRequest.requestURI.removePrefix(prefix.orEmpty())}"
        } else {
            "$targetUrl${proxyRequest.requestURI.removePrefix(prefix.orEmpty())}?${proxyRequest.queryString}"
        }
        val newRequest = Request.Builder()
            .url(newUrl)
            .apply {
                proxyRequest.headers().forEach { (key, value) -> this.header(key, value) }
            }
            .method(proxyRequest.method, proxyRequest.body())
            .build()
            .let { proxyCallHandler.before(proxyRequest, proxyResponse, it) }
        val newResponse = client
            .newCall(newRequest)
            .execute()
        proxyRequest.accessLog(newResponse)
        proxyCallHandler.after(proxyRequest, proxyResponse, newResponse)
    }

    private fun HttpServletRequest.accessLog(upRes: Response) {
        var user = "-"
        if (getHeader(HttpHeaders.AUTHORIZATION).orEmpty().startsWith(BASIC_AUTH_PREFIX)) {
            val authorizationHeader = getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
            user = BasicAuthUtils.decode(authorizationHeader).first
        }
        val requestTime = System.currentTimeMillis() - upRes.sentRequestAtMillis
        val httpUserAgent = getHeader(HttpHeaders.USER_AGENT)
        val url = upRes.request.url.host
        val requestBodyBytes = contentLengthLong
        logger.info(
            "\"$method $requestURI $protocol\" - " +
                "user:$user up_status: ${upRes.code} ms:$requestTime up:$url agent:$httpUserAgent $requestBodyBytes",
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HttpProxyUtil::class.java)

        fun HttpServletRequest.headers(): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            val headerNames = this.headerNames
            while (headerNames.hasMoreElements()) {
                val headerName = headerNames.nextElement()
                headers[headerName] = this.getHeader(headerName)
            }
            return headers
        }

        fun HttpServletRequest.body(): RequestBody? {
            val isChunked = headers()[HttpHeaders.TRANSFER_ENCODING] == "chunked"
            if (this.contentLengthLong <= 0 && !isChunked) {
                return null
            }
            val mediaType = this.contentType?.toMediaTypeOrNull()
            val inputStream = this.inputStream
            val contentLength = this.contentLengthLong
            return object : RequestBody() {
                override fun contentType(): MediaType? = mediaType

                override fun contentLength(): Long = contentLength

                override fun writeTo(sink: BufferedSink) {
                    inputStream.source().use {
                        sink.writeAll(it)
                    }
                }
            }
        }
    }
}
