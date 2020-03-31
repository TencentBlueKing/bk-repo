package com.tencent.bkrepo.common.stream.binder.noop

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NoOpBinderConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun noOpBinder() = NoOpBinder()
}
