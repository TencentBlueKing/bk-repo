package com.tencent.bkrepo.common.service.security

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import feign.RequestInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@EnableConfigurationProperties(SecurityProperties::class)
@Import(SecurityAuthenticateManager::class)
class SecurityConfiguration {

    @Bean
    fun securityRequestInterceptor(securityAuthenticateManager: SecurityAuthenticateManager): RequestInterceptor {
        return RequestInterceptor { requestTemplate ->
            requestTemplate.header(MS_AUTH_HEADER_SECURITY_TOKEN, securityAuthenticateManager.getSecurityToken())
        }
    }

    @Bean
    fun securityAuthenticateInterceptor() = SecurityAuthenticateInterceptor()

    @Bean
    fun commonWebMvcConfigurer(securityAuthenticateInterceptor: SecurityAuthenticateInterceptor): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(securityAuthenticateInterceptor).addPathPatterns(listOf("/service/**"))
                super.addInterceptors(registry)
            }
        }
    }
}
