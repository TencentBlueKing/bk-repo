package com.tencent.bkrepo.common.service

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.async.AsyncConfiguration
import com.tencent.bkrepo.common.service.exception.GlobalExceptionHandler
import com.tencent.bkrepo.common.service.feign.ClientConfiguration
import com.tencent.bkrepo.common.service.feign.FeignFilterRequestMappingHandlerMapping
import com.tencent.bkrepo.common.service.log.AccessLogWebServerCustomizer
import com.tencent.bkrepo.common.service.message.MessageSourceConfiguration
import com.tencent.bkrepo.common.service.ribbon.RibbonGrayConfiguration
import com.tencent.bkrepo.common.service.security.SecurityConfiguration
import com.tencent.bkrepo.common.service.swagger.SwaggerConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.Ordered
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@PropertySource("classpath:common-service.properties")
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@EnableAsync
@Import(
    SwaggerConfiguration::class,
    GlobalExceptionHandler::class,
    AsyncConfiguration::class,
    MessageSourceConfiguration::class,
    ClientConfiguration::class,
    RibbonGrayConfiguration::class,
    AccessLogWebServerCustomizer::class,
    SecurityConfiguration::class
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
    fun objectMapper() = JsonUtils.objectMapper

    @Bean
    fun mappingJackson2HttpMessageConverter(): MappingJackson2HttpMessageConverter {
        return MappingJackson2HttpMessageConverter(JsonUtils.objectMapper)
    }
}
