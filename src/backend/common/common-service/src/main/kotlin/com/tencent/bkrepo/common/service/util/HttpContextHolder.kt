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
object HttpContextHolder {
    fun getRequest(): HttpServletRequest {
        return (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
    }

    fun getResponse(): HttpServletResponse {
        return (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response!!
    }

}