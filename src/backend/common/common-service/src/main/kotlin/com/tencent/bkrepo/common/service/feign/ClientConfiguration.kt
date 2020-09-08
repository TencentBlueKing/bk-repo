package com.tencent.bkrepo.common.service.feign

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import feign.RequestInterceptor
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableFeignClients(basePackages = ["com.tencent.bkrepo"])
class ClientConfiguration {

    @Bean
    fun requestInterceptor(): RequestInterceptor {
        return RequestInterceptor { requestTemplate ->
            // 设置Accept-Language请求头
            HttpContextHolder.getRequestOrNull()?.getHeader(HttpHeaders.ACCEPT_LANGUAGE)?.let {
                requestTemplate.header(HttpHeaders.ACCEPT_LANGUAGE, it)
            }
        }
    }

    @Bean
    fun errorCodeDecoder() = ErrorCodeDecoder()
}
