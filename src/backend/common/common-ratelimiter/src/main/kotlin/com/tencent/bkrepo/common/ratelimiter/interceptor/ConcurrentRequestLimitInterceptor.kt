package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.service.concurrent.UrlConcurrentRequestLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.concurrent.UserUrlConcurrentRequestLimiterService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * URL并发请求限流拦截器
 * 用于限制特定URL的并发执行数量，防止数据库高负载
 */
class ConcurrentRequestLimitInterceptor(
    private val urlConcurrentRequestLimiterService: UrlConcurrentRequestLimiterService?,
    private val userUrlConcurrentRequestLimiterService: UserUrlConcurrentRequestLimiterService?
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        var urlChecked = false
        var userUrlChecked = false

        try {
            urlConcurrentRequestLimiterService?.let {
                if (!it.ignoreRequest(request)) {
                    it.limit(request)
                    urlChecked = true
                    request.setAttribute(URL_CONCURRENT_CHECKED, true)
                }
            }

            userUrlConcurrentRequestLimiterService?.let {
                if (!it.ignoreRequest(request)) {
                    it.limit(request)
                    userUrlChecked = true
                    request.setAttribute(USER_URL_CONCURRENT_CHECKED, true)
                }
            }

            return true
        } catch (e: Exception) {
            if (urlChecked) {
                urlConcurrentRequestLimiterService?.finish(request)
            }
            if (userUrlChecked) {
                userUrlConcurrentRequestLimiterService?.finish(request)
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
        if (request.getAttribute(URL_CONCURRENT_CHECKED) == true) {
            urlConcurrentRequestLimiterService?.finish(request)
            request.removeAttribute(URL_CONCURRENT_CHECKED)
        }

        if (request.getAttribute(USER_URL_CONCURRENT_CHECKED) == true) {
            userUrlConcurrentRequestLimiterService?.finish(request)
            request.removeAttribute(USER_URL_CONCURRENT_CHECKED)
        }
    }

    companion object {
        private const val URL_CONCURRENT_CHECKED = "URL_CONCURRENT_CHECKED"
        private const val USER_URL_CONCURRENT_CHECKED = "USER_URL_CONCURRENT_CHECKED"
    }
}
