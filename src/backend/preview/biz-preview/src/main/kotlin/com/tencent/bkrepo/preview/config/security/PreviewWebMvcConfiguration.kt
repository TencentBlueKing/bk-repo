package com.tencent.bkrepo.preview.config.security

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 注册 preview 阶段二临时 token 拦截器。
 *
 * 路径与 [com.tencent.bkrepo.preview.config.configuration.PreviewArtifactConfigurer] 中
 * `includePattern("/api/…")` 保持一致。
 * 通过 [LazyPreviewTokenPostAuthInterceptor] 延迟解析 [PreviewTokenAuthService]，
 * 避免启动期与 Feign / MVC 基础设施产生循环依赖。
 */
@Configuration(proxyBeanMethods = false)
class PreviewWebMvcConfiguration(
    private val previewTokenAuthServiceProvider: ObjectProvider<PreviewTokenAuthService>,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(LazyPreviewTokenPostAuthInterceptor(previewTokenAuthServiceProvider))
            .addPathPatterns(PREVIEW_API_PATH_PATTERN)
            .order(POST_AUTH_INTERCEPTOR_ORDER)
    }

    private companion object {
        const val PREVIEW_API_PATH_PATTERN = "/api/**"
        const val POST_AUTH_INTERCEPTOR_ORDER = 1
    }
}
