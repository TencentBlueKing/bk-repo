package com.tencent.bkrepo.preview.config.security

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.AsyncHandlerInterceptor

/**
 * preview 临时 token 阶段二鉴权拦截器。
 *
 * 在 [com.tencent.bkrepo.common.security.http.core.HttpAuthInterceptor] 之后执行：
 * 无 token 时快速跳过；有 token 时强制走 [PreviewTokenAuthService] 完整校验（fail-close）。
 */
class PreviewTokenPostAuthInterceptor(
    private val previewTokenAuthService: PreviewTokenAuthService,
) : AsyncHandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.dispatcherType != DispatcherType.REQUEST) {
            return true
        }
        previewTokenAuthService.authenticateIfPresent(request)
        return true
    }
}
