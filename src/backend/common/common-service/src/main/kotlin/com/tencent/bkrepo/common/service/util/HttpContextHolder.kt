package com.tencent.bkrepo.common.service.util

import com.tencent.bkrepo.common.api.constant.StringPool
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.StringTokenizer
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
object HttpContextHolder {
    fun getRequest(): HttpServletRequest {
        return (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
    }

    fun getResponse(): HttpServletResponse {
        return (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response!!
    }

    fun getClientAddress(): String {
        return (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.let {
            val header = it.getHeader("X-Forwarded-For")
            if (header.isNullOrBlank()) it.remoteAddr else StringTokenizer(header, ",").nextToken()
        } ?: StringPool.UNKNOWN
    }
}
