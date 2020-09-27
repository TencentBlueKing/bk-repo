package com.tencent.bkrepo.auth.config

import com.tencent.bkrepo.auth.interceptor.AuthInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AuthConfig : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(clientAuthInterceptor())
            .addPathPatterns("/api/**")
            .excludePathPatterns("/external/**")
            .order(0)
        super.addInterceptors(registry)
    }

    @Bean
    fun clientAuthInterceptor() = AuthInterceptor()
}
