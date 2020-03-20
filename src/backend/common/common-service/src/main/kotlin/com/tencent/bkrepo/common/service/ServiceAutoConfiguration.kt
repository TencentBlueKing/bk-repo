package com.tencent.bkrepo.common.service

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.auth.MicroServiceAuthInterceptor
import com.tencent.bkrepo.common.service.exception.GlobalExceptionHandler
import com.tencent.bkrepo.common.service.feign.ClientConfiguration
import com.tencent.bkrepo.common.service.feign.FeignFilterRequestMappingHandlerMapping
import com.tencent.bkrepo.common.service.message.MessageSourceConfiguration
import com.tencent.bkrepo.common.service.ribbon.RibbonRouteRuleConfiguration
import com.tencent.bkrepo.common.service.swagger.SwaggerAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@EnableDiscoveryClient
@Import(
    SwaggerAutoConfiguration::class,
    GlobalExceptionHandler::class,
    MessageSourceConfiguration::class,
    ClientConfiguration::class,
    RibbonRouteRuleConfiguration::class
)
class ServiceAutoConfiguration {

    @Bean
    fun feignWebRegistrations(): WebMvcRegistrations {
        return object : WebMvcRegistrations {
            override fun getRequestMappingHandlerMapping() =
                FeignFilterRequestMappingHandlerMapping()
        }
    }

    @Bean
    fun commonWebMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(microServiceAuthInterceptor()).addPathPatterns(listOf("/service/**"))
                super.addInterceptors(registry)
            }
        }
    }

    @Bean
    fun objectMapper() = JsonUtils.objectMapper

    @Bean
    fun microServiceAuthInterceptor() = MicroServiceAuthInterceptor()

    @Bean
    fun mappingJackson2HttpMessageConverter(): MappingJackson2HttpMessageConverter {
        return MappingJackson2HttpMessageConverter(JsonUtils.objectMapper)
    }
}
