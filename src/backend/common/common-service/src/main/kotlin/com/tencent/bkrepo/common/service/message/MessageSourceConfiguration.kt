package com.tencent.bkrepo.common.service.message

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessageSourceConfiguration {

    @Bean
    fun messageSource(): MessageSource {
        val messageSource = PathMatchingResourceBundleMessageSource()
        messageSource.setBasename("classpath*:i18n/messages")
        messageSource.setDefaultEncoding("UTF-8")
        return messageSource
    }
}
