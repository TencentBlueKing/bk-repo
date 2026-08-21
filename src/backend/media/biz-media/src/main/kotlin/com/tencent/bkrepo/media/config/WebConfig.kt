package com.tencent.bkrepo.media.config

import com.tencent.bkrepo.media.web.MediaCosRepoInterceptor
import com.tencent.bkrepo.media.web.PluginDelegateFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val mediaCosRepoInterceptor: MediaCosRepoInterceptor,
) : WebMvcConfigurer {

    @Bean
    fun pluginDelegateFilter(): FilterRegistrationBean<PluginDelegateFilter> {
        val registrationBean = FilterRegistrationBean<PluginDelegateFilter>()
        registrationBean.filter = PluginDelegateFilter()
        return registrationBean
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(mediaCosRepoInterceptor)
            .addPathPatterns(
                "/user/cos/upload/**",
                "/media/user/cos/upload/**",
            )
        super.addInterceptors(registry)
    }
}
