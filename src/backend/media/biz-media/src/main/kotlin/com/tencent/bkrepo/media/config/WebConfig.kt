package com.tencent.bkrepo.media.config

import com.tencent.bkrepo.media.web.PluginDelegateFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebConfig {
    @Bean
    fun pluginDelegateFilter(): FilterRegistrationBean<PluginDelegateFilter> {
        val registrationBean = FilterRegistrationBean<PluginDelegateFilter>()
        registrationBean.filter = PluginDelegateFilter()
        return registrationBean
    }
}
