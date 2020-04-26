package com.tencent.bkrepo.common.service

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.async.AsyncConfiguration
import com.tencent.bkrepo.common.service.auth.MicroServiceAuthInterceptor
import com.tencent.bkrepo.common.service.exception.GlobalExceptionHandler
import com.tencent.bkrepo.common.service.feign.ClientConfiguration
import com.tencent.bkrepo.common.service.feign.FeignFilterRequestMappingHandlerMapping
import com.tencent.bkrepo.common.service.log.AccessLogWebServerCustomizer
import com.tencent.bkrepo.common.service.message.MessageSourceConfiguration
import com.tencent.bkrepo.common.service.ribbon.RibbonRouteRuleConfiguration
import com.tencent.bkrepo.common.service.swagger.SwaggerConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.Ordered
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@PropertySource("classpath:common-service.yml")
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@EnableDiscoveryClient
@EnableAsync
@Import(
    SwaggerConfiguration::class,
    GlobalExceptionHandler::class,
    AsyncConfiguration::class,
    MessageSourceConfiguration::class,
    ClientConfiguration::class,
    RibbonRouteRuleConfiguration::class,
    AccessLogWebServerCustomizer::class
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
