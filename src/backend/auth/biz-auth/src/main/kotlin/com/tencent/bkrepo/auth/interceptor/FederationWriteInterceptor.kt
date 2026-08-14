package com.tencent.bkrepo.auth.interceptor

import com.tencent.bkrepo.auth.context.FederationWriteContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 读取 X-Federation-Write 请求头，标记当前请求为联邦复制写操作。
 * auth 服务的各 publishEvent 方法在检测到该标记时跳过事件发布，防止事件风暴。
 */
class FederationWriteInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.getHeader(HEADER_NAME) == HEADER_VALUE) {
            FederationWriteContext.markAsFederationWrite()
        }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        FederationWriteContext.clear()
    }

    companion object {
        const val HEADER_NAME = "X-Federation-Write"
        const val HEADER_VALUE = "true"
    }
}
