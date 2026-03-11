package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.repository.interceptor.DriveProxyHandler
import com.tencent.bkrepo.repository.interceptor.DriveProxyInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Drive 代理配置
 * 只有在配置 repository.drive.enabled=true 时才会注册拦截器
 */
@Configuration
class DriveConfig(
    private val properties: DriveProperties,
    private val principalManagerProvider: ObjectProvider<PrincipalManager>
) : WebMvcConfigurer {
    
    override fun addInterceptors(registry: InterceptorRegistry) {
        // 只有在配置启用时才注册拦截器
        if (!properties.enabled) {
            logger.info("Drive proxy feature is disabled, skip registering DriveProxyInterceptor")
            return
        }
        
        logger.info("Drive proxy feature is enabled, registering DriveProxyInterceptor")
        val proxyHandler = DriveProxyHandler(properties)
        // 使用 ObjectProvider 延迟获取 PrincipalManager，避免循环依赖
        registry.addInterceptor(DriveProxyInterceptor(proxyHandler, properties, principalManagerProvider))
            .addPathPatterns("/api/drive/ci/**")
            .order(Ordered.LOWEST_PRECEDENCE)
        super.addInterceptors(registry)
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(DriveConfig::class.java)
    }
}
