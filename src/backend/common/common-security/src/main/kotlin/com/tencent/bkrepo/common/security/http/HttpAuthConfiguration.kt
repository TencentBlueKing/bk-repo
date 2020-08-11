package com.tencent.bkrepo.common.security.http

import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.http.login.LoginConfiguration
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(HttpAuthProperties::class, JwtAuthProperties::class)
@Import(LoginConfiguration::class, AuthenticationManager::class)
class HttpAuthConfiguration {

    @Bean
    fun httpAuthConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                val httpAuthSecurity = httpAuthSecurity()
                registry.addInterceptor(httpAuthInterceptor())
                    .excludePathPatterns(httpAuthSecurity.getExcludePatterns())
            }
        }
    }

    @Bean
    fun httpAuthSecurity() = HttpAuthSecurity()

    @Bean
    fun httpAuthInterceptor() = HttpAuthInterceptor()
}
