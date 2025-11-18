package com.tencent.bkrepo.common.artifact.crypt

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.constant.HEADER_CRYPT_KEY
import com.tencent.bkrepo.common.security.util.RsaUtils
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod

class CryptFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        if (request !is HttpServletRequest) {
            chain.doFilter(request, response)
            return
        }
        val key = getKey(request)
        if (key.isNullOrEmpty()) {
            chain.doFilter(request, response)
            return
        }
        if (request.method == HttpMethod.GET.name()) {
            // 下载请求
            val responseWrapper = HttpDecryptResponseWrapper(key, response as HttpServletResponse)
            val requestWrapper = HttpDownloadRequestWrapper(request)
            chain.doFilter(requestWrapper, responseWrapper)
        } else if (request.method == HttpMethod.PUT.name()) {
            // 上传请求
            val requestWrapper = HttpEncryptRequestWrapper(key, request)
            chain.doFilter(requestWrapper, response)
        }
    }

    private fun getKey(request: HttpServletRequest): String? {
        val keyHeader = request.getHeader(HEADER_CRYPT_KEY) ?: return null
        val cryptKey = try {
            val data = RsaUtils.decrypt(keyHeader)
            data.readJsonString<CryptKey>()
        } catch (e: Exception) {
            logger.warn("Crypt key[$keyHeader] is invalid", e)
            null
        } ?: return null
        if (cryptKey.timestamp + cryptKey.expiredSeconds < System.currentTimeMillis()/1000L) {
            return null
        }
        return cryptKey.key
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CryptFilter::class.java)
    }
}