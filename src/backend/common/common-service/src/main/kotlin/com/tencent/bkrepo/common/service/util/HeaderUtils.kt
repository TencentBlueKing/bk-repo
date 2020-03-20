package com.tencent.bkrepo.common.service.util

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.net.URLDecoder

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
object HeaderUtils {

    fun getHeader(name: String): String? {
        return request().getHeader(name)
    }

    fun getLongHeader(name: String): Long {
        return getHeader(name)?.toLong() ?: 0L
    }

    fun getBooleanHeader(name: String): Boolean {
        return getHeader(name)?.toBoolean() ?: false
    }

    fun getUrlDecodedHeader(name: String): String? {
        return getHeader(name)?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (exception: Exception) {
                it
            }
        }
    }

    private fun request() = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
}
