package com.tencent.bkrepo.common.service.util

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
object HeaderUtils {

    fun getHeader(name: String): String? {
        return request().getHeader(name)
    }

    fun getLongHeader(header: String): Long {
        return getHeader(header)?.toLong() ?: 0L
    }

    fun getBooleanHeader(header: String): Boolean {
        return getHeader(header)?.toBoolean() ?: false

    }

    private fun request() = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
}