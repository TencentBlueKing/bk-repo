package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.repository.interceptor.DriveProxyHandler
import com.tencent.bkrepo.repository.interceptor.DriveProxyInterceptor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(DriveProperties::class)
class DriveConfig(
    private val properties: DriveProperties,
    private val principalManagerProvider: ObjectProvider<PrincipalManager>
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        val proxyHandler = DriveProxyHandler(properties)
        // 使用 ObjectProvider 延迟获取 PrincipalManager，避免循环依赖
        registry.addInterceptor(DriveProxyInterceptor(proxyHandler, properties, principalManagerProvider))
            .addPathPatterns("/api/drive/**")
            .order(Ordered.LOWEST_PRECEDENCE)
        super.addInterceptors(registry)
    }
}
