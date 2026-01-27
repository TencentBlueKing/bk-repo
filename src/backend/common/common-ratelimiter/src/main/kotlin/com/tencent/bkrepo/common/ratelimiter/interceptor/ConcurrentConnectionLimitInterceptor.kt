package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.service.connection.IpConcurrentConnectionLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.connection.UserConcurrentConnectionLimiterService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 用户和IP并发连接数限流拦截器
 */
class ConcurrentConnectionLimitInterceptor(
    private val userConnectionLimiterService: UserConcurrentConnectionLimiterService?,
    private val ipConnectionLimiterService: IpConcurrentConnectionLimiterService?
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        var userChecked = false
        var ipChecked = false

        try {
            userConnectionLimiterService?.let {
                it.limit(request)
                userChecked = true
                request.setAttribute(USER_CONNECTION_CHECKED, true)
            }

            ipConnectionLimiterService?.let {
                it.limit(request)
                ipChecked = true
                request.setAttribute(IP_CONNECTION_CHECKED, true)
            }

            return true
        } catch (e: Exception) {
            if (userChecked) {
                userConnectionLimiterService?.finish(request)
            }
            if (ipChecked) {
                ipConnectionLimiterService?.finish(request)
            }
            throw e
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        if (request.getAttribute(USER_CONNECTION_CHECKED) == true) {
            userConnectionLimiterService?.finish(request)
            request.removeAttribute(USER_CONNECTION_CHECKED)
        }

        if (request.getAttribute(IP_CONNECTION_CHECKED) == true) {
            ipConnectionLimiterService?.finish(request)
            request.removeAttribute(IP_CONNECTION_CHECKED)
        }
    }

    companion object {
        private const val USER_CONNECTION_CHECKED = "USER_CONNECTION_CHECKED"
        private const val IP_CONNECTION_CHECKED = "IP_CONNECTION_CHECKED"
    }
}
