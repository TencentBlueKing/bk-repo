package com.tencent.bkrepo.common.client

import feign.RequestInterceptor
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Configuration
@EnableFeignClients(basePackages = ["com.tencent.bkrepo"])
class ClientAutoConfiguration {

    @Bean
    fun requestInterceptor(): RequestInterceptor {
        return RequestInterceptor { requestTemplate ->
            val attributes =
                    RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return@RequestInterceptor
            val request = attributes.request
            val languageHeaderName = "Accept-Language"
            val languageHeaderValue = request.getHeader(languageHeaderName)
            if (!languageHeaderValue.isNullOrBlank()) {
                requestTemplate.header(languageHeaderName, languageHeaderValue) // 设置Accept-Language请求头
            }
            val cookies = request.cookies
            if (cookies != null && cookies.isNotEmpty()) {
                val cookieBuilder = StringBuilder()
                cookies.forEach {
                    cookieBuilder.append(it.name).append("=").append(it.value).append(";")
                }
                requestTemplate.header("Cookie", cookieBuilder.toString()) // 设置cookie信息
            }
        }
    }
}
