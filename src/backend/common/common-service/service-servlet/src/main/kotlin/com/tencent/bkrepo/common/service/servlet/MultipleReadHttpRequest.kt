package com.tencent.bkrepo.common.service.servlet

import com.tencent.bkrepo.common.api.stream.LimitByteArrayOutputStream
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

/**
 * 支持多次读取的请求
 *
 * 使用内存对原始请求进行缓存，以供多次读取
 *
 * @param request 原始请求
 * @param limit 缓存限制大小
 * */
class MultipleReadHttpRequest(val request: HttpServletRequest, val limit: Long) : HttpServletRequestWrapper(request) {

    private var cacheBytes: ByteArrayOutputStream? = null

    override fun getInputStream(): ServletInputStream {
        if (cacheBytes == null) {
            cacheInputStream()
        }
        return CachedServletInputStream(cacheBytes!!.toByteArray())
    }

    override fun getReader(): BufferedReader {
        return BufferedReader(InputStreamReader(inputStream))
    }

    private fun cacheInputStream() {
        cacheBytes = LimitByteArrayOutputStream(limit)
        request.inputStream.copyTo(cacheBytes!!)
    }
}
