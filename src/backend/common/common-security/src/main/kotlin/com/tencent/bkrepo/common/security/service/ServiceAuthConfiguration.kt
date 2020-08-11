package com.tencent.bkrepo.common.security.service

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import feign.RequestInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(ServiceAuthProperties::class)
@Import(ServiceAuthManager::class)
class ServiceAuthConfiguration {

    @Bean
    fun securityRequestInterceptor(serviceAuthManager: ServiceAuthManager): RequestInterceptor {
        return RequestInterceptor { requestTemplate ->
            requestTemplate.header(MS_AUTH_HEADER_SECURITY_TOKEN, serviceAuthManager.getSecurityToken())
        }
    }

    @Bean
    fun serviceAuthInterceptor(
        serviceAuthManager: ServiceAuthManager,
        serviceAuthProperties: ServiceAuthProperties
    ): ServiceAuthInterceptor {
        return ServiceAuthInterceptor(serviceAuthManager, serviceAuthProperties)
    }

    @Bean
    fun serviceAuthWebMvcConfigurer(serviceAuthInterceptor: ServiceAuthInterceptor): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(serviceAuthInterceptor).addPathPatterns(listOf("/service/**"))
                super.addInterceptors(registry)
            }
        }
    }
}
