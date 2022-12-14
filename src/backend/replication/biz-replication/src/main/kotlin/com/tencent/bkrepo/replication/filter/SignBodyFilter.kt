package com.tencent.bkrepo.replication.filter

import com.google.common.hash.Hashing
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.security.util.HttpSigner
import com.tencent.bkrepo.common.service.servlet.MultipleReadHttpRequest
import java.io.ByteArrayOutputStream
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import org.springframework.http.MediaType

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
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request.contentLength > 0 &&
            !request.contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)
        ) {
            // 限制缓存大小
            val multiReadRequest = MultipleReadHttpRequest(request as HttpServletRequest, limit)
            val body = ByteArrayOutputStream()
            multiReadRequest.inputStream.copyTo(body)
            val bodyHash = Hashing.sha256().hashBytes(body.toByteArray())
            multiReadRequest.setAttribute(HttpSigner.SIGN_BODY, bodyHash)
            chain.doFilter(multiReadRequest, response)
        } else {
            val bodyHash = Hashing.sha256().hashBytes(StringPool.EMPTY.toByteArray())
            request.setAttribute(HttpSigner.SIGN_BODY, bodyHash)
            chain.doFilter(request, response)
        }
    }
}
