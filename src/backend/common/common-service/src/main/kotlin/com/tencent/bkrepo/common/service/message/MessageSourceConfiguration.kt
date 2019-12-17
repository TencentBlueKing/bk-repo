package com.tencent.bkrepo.common.service.message

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessageSourceConfiguration {

    /**
     * i18n规则：通过Accept-Language头指定语言，MessageSource会搜索classpath的i18n目录下指定语言的messages_*.properties文件。
     * 如果没找到，并且FallbackToSystemLocale=true，则会使用系统默认locale，默认locale根据"user.language"变量进行选择。
     * 如果FallbackToSystemLocale=false，则会使用i18n/messages_en.properties。
     * FallbackToSystemLocale默认为true
     */
    @Bean
    fun messageSource(): MessageSource {
        val messageSource = PathMatchingResourceBundleMessageSource()
        messageSource.setBasename("classpath*:i18n/messages")
        messageSource.setDefaultEncoding("UTF-8")
        messageSource.setUseCodeAsDefaultMessage(true)
        return messageSource
    }
}
