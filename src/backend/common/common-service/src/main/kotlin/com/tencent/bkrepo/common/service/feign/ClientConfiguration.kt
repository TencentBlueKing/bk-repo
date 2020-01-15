package com.tencent.bkrepo.common.service.feign

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import feign.RequestInterceptor
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.cloud.openfeign.FeignLoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Configuration
@EnableFeignClients(basePackages = ["com.tencent.bkrepo"])
class ClientConfiguration {

    @Bean
    fun requestInterceptor(): RequestInterceptor {
        return RequestInterceptor { requestTemplate ->
            val attributes =
                    RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return@RequestInterceptor
            val request = attributes.request
            // language
            val languageHeaderName = "Accept-Language"
            val languageHeaderValue = request.getHeader(languageHeaderName)
            if (!languageHeaderValue.isNullOrBlank()) {
                requestTemplate.header(languageHeaderName, languageHeaderValue) // 设置Accept-Language请求头
            }
            // user
            val userId = request.getAttribute(USER_KEY) as? String
            if(userId != null) {
                requestTemplate.header(MS_AUTH_HEADER_UID, userId) // 设置uid请求头
            }
        }
    }

    @Bean
    fun feignLoggerFactory(): FeignLoggerFactory {
        val feignLogger = FeignApiLogger()
        return FeignLoggerFactory { feignLogger }
    }

    @Bean
    fun errorCodeDecoder() = ErrorCodeDecoder()
}
