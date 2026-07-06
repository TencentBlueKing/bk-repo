package com.tencent.bkrepo.preview.config.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.servlet.AsyncHandlerInterceptor

/**
 * 延迟解析 [PreviewTokenPostAuthInterceptor]，避免 MVC 配置阶段提前初始化
 * [PreviewTokenAuthService] / [com.tencent.bkrepo.common.security.manager.AuthenticationManager]，
 * 从而与 Feign 客户端创建产生循环依赖。
 */
class LazyPreviewTokenPostAuthInterceptor(
    private val previewTokenAuthServiceProvider: ObjectProvider<PreviewTokenAuthService>,
) : AsyncHandlerInterceptor {

    private val delegate by lazy {
        PreviewTokenPostAuthInterceptor(previewTokenAuthServiceProvider.getObject())
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        return delegate.preHandle(request, response, handler)
    }
}
