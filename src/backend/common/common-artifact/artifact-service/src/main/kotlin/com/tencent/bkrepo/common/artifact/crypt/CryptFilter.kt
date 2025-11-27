package com.tencent.bkrepo.common.artifact.crypt

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.HEADER_CRYPT_KEY
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod

class CryptFilter(
    private val cryptKeyDecryptor: CryptKeyDecryptor
) : Filter {

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        if (request !is HttpServletRequest || response !is HttpServletResponse) {
            chain.doFilter(request, response)
            return
        }
        val keyHeader = request.getHeader(HEADER_CRYPT_KEY) ?: run {
            chain.doFilter(request, response)
            return
        }
        val key = try {
            cryptKeyDecryptor.getKey(keyHeader)
        } catch (e: ErrorCodeException) {
            response.status = e.status.value
            val errorMessage = LocaleMessageUtils.getLocalizedMessage(e.messageCode, e.params)
            val res = ResponseBuilder.fail(e.messageCode.getCode(), errorMessage)
            response.contentType = MediaTypes.APPLICATION_JSON
            response.writer.print(res.toJsonString())
            return
        }
        if (key.isEmpty()) {
            chain.doFilter(request, response)
            return
        }
        if (request.method == HttpMethod.GET.name()) {
            // 下载请求
            val responseWrapper = HttpDecryptResponseWrapper(key, response)
            val requestWrapper = HttpDownloadRequestWrapper(request)
            chain.doFilter(requestWrapper, responseWrapper)
        } else if (request.method == HttpMethod.PUT.name()) {
            // 上传请求
            val requestWrapper = HttpEncryptRequestWrapper(key, request)
            chain.doFilter(requestWrapper, response)
        }
    }
}