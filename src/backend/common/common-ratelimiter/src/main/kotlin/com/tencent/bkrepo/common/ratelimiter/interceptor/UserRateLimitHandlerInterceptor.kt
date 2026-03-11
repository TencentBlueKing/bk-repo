package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 针对需要用户校验的http请求添加限流拦截
 * 该拦截器应在用户登录鉴权后执行
 */
class UserRateLimitHandlerInterceptor(
    private val requestLimitCheckService: RequestLimitCheckService
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        requestLimitCheckService.preLimitCheckForUser(request)
        return super.preHandle(request, response, handler)
    }
}

