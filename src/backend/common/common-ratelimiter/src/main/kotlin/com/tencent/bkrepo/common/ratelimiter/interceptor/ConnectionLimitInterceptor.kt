package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.service.connection.ServiceInstanceConnectionLimiterService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 服务实例连接数限流拦截器
 * 在请求开始时进行限流检查，请求完成后调用 finish 方法
 */
class ConnectionLimitInterceptor(
    private val connectionLimiterService: ServiceInstanceConnectionLimiterService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        try {
            val startTime = System.nanoTime()
            request.setAttribute(CONNECTION_START_TIME_ATTRIBUTE, startTime)

            connectionLimiterService.limit(request)
            // 标记限流已检查，用于 afterCompletion
            request.setAttribute(CONNECTION_CHECKED_ATTRIBUTE, true)
            return true
        } catch (e: Exception) {
            logger.warn("Connection limit check failed: ${e.message}")
            throw e
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        // 只有成功通过限流检查的请求才需要调用 finish
        if (request.getAttribute(CONNECTION_CHECKED_ATTRIBUTE) == true) {
            val startTime = request.getAttribute(CONNECTION_START_TIME_ATTRIBUTE) as? Long
            connectionLimiterService.finish(request, ex, startTime)
            request.removeAttribute(CONNECTION_CHECKED_ATTRIBUTE)
            request.removeAttribute(CONNECTION_START_TIME_ATTRIBUTE)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionLimitInterceptor::class.java)
        private const val CONNECTION_CHECKED_ATTRIBUTE = "CONNECTION_CHECKED"
        private const val CONNECTION_START_TIME_ATTRIBUTE = "CONNECTION_START_TIME"
    }
}



