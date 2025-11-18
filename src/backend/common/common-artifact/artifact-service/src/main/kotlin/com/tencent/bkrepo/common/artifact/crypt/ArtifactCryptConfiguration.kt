package com.tencent.bkrepo.common.artifact.crypt

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ArtifactCryptConfiguration {

    @Value("artifact.crypt.key")
    private val key: String = "bkrepo@secret"

    @Bean
    fun cryptFilter(): CryptFilter {
        return CryptFilter()
    }

    @Bean
    fun secretKeyProvider(): SecretKeyProvider {
        return SecretKeyProvider(key)
    }
}