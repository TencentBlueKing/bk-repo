package com.tencent.bkrepo.common.security.http.sign

import com.google.common.hash.Hashing
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.service.servlet.MultipleReadHttpRequest
import com.tencent.bkrepo.common.service.util.HttpSigner
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream

/**
 * 对body进行签名的过滤器。
 *
 * 主要完成：
 * 1. 对非multipart/form-data请求的body进行sha256签名，并添加到attribute
 * 2. 对原始请求进行包装，使其再后续链路中可被正确读取。
 *
 * @param limit 限制缓存body的大小，防止缓存的body过大，导致程序内存不足
 * */
class SignBodyFilter(private val limit: Long) : Filter {

    private val emptyStringHash = Hashing.sha256().hashBytes(StringPool.EMPTY.toByteArray())

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val lengthCondition = request.contentLength in 1..limit
        val typeCondition = request.contentType?.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE) == false &&
            request.contentType?.startsWith(MediaType.APPLICATION_OCTET_STREAM_VALUE) == false &&
            request.contentType?.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE) == false
        if (lengthCondition && typeCondition) {
            // 限制缓存大小
            val multiReadRequest = MultipleReadHttpRequest(request as HttpServletRequest, limit)
            val body = ByteArrayOutputStream()
            multiReadRequest.inputStream.copyTo(body)
            val sig = request.getParameter(HttpSigner.SIGN)
            val appId = request.getParameter(HttpSigner.APP_ID)
            val accessKey = request.getParameter(HttpSigner.ACCESS_KEY)
            if (sig != null && appId != null && accessKey != null) {
                val bodyHash = Hashing.sha256().hashBytes(body.toByteArray())
                multiReadRequest.setAttribute(HttpSigner.SIGN_BODY, bodyHash)
            }
            chain.doFilter(multiReadRequest, response)
        } else {
            val bodyHash = emptyStringHash
            request.setAttribute(HttpSigner.SIGN_BODY, bodyHash)
            chain.doFilter(request, response)
        }
    }
}
