package com.tencent.bkrepo.git.config

import com.tencent.bkrepo.git.artifact.GitRepoInterceptor
import com.tencent.bkrepo.git.interceptor.ContextSettingInterceptor
import com.tencent.bkrepo.git.interceptor.ProxyInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(GitProperties::class)
class GitWebConfig : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(repoInterceptor())
            .addPathPatterns("/**")
            .order(Ordered.LOWEST_PRECEDENCE)
        registry.addInterceptor(ContextSettingInterceptor())
            .addPathPatterns("/**")
            .order(Ordered.LOWEST_PRECEDENCE)
        registry.addInterceptor(ProxyInterceptor())
            .addPathPatterns("/**")
            .order(Ordered.HIGHEST_PRECEDENCE + 1)
        super.addInterceptors(registry)
    }

    @Bean
    fun repoInterceptor() = GitRepoInterceptor()
}
