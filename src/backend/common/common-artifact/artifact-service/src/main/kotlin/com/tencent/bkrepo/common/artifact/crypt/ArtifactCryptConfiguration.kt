package com.tencent.bkrepo.common.artifact.crypt

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ArtifactCryptConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["artifact.crypt.enabled"], havingValue = "true")
    fun cryptFilter(): CryptFilter {
        return CryptFilter()
    }
}