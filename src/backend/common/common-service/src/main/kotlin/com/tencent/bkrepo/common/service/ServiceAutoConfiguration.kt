package com.tencent.bkrepo.common.service

import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.data.domain.AuditorAware
import java.util.*

@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@EnableDiscoveryClient
class ServiceAutoConfiguration {

    @Bean
    fun userAuditor(): AuditorAware<String> {
        return AuditorAware {
            // TODO: 返回操作用户
            Optional.of("system")
        }
    }

}
