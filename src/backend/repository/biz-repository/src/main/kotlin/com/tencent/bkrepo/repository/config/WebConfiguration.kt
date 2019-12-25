package com.tencent.bkrepo.repository.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 *
 * @author: carrypan
 * @date: 2019/12/23
 */
@Configuration
class WebConfiguration {

    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(platformAuthInterceptor()).addPathPatterns(listOf("/api/**"))
                super.addInterceptors(registry)
            }
        }
    }

    @Bean
    fun platformAuthInterceptor() = PlatformAuthInterceptor()
}