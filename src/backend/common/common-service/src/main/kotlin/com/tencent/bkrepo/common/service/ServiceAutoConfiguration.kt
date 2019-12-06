package com.tencent.bkrepo.common.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.config.FeignFilterRequestMappingHandlerMapping
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@EnableDiscoveryClient
class ServiceAutoConfiguration {
    @Bean
    fun feignWebRegistrations(): WebMvcRegistrations {
        return object : WebMvcRegistrations {
            override fun getRequestMappingHandlerMapping() = FeignFilterRequestMappingHandlerMapping()
        }
    }

    @Bean
    fun objectMapper(): ObjectMapper {
        return JsonUtils.objectMapper
    }

    @Bean
    fun mappingJackson2HttpMessageConverter(): MappingJackson2HttpMessageConverter {
        return MappingJackson2HttpMessageConverter(JsonUtils.objectMapper)
    }
}
