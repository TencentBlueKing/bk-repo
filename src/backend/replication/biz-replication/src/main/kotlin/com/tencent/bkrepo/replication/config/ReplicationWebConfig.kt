package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.replication.filter.SignBodyFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReplicationWebConfig {
    @Bean
    fun signBodyFilter(replicationProperties: ReplicationProperties): FilterRegistrationBean<SignBodyFilter> {
        val registrationBean = FilterRegistrationBean<SignBodyFilter>()
        registrationBean.filter = SignBodyFilter(replicationProperties.bodyLimit.toBytes())
        registrationBean.addUrlPatterns("/replica/*")
        registrationBean.addUrlPatterns("/replication/replica/*")
        registrationBean.order = 0
        return registrationBean
    }
}
