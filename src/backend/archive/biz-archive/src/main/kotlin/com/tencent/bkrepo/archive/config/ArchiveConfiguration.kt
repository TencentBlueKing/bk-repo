package com.tencent.bkrepo.archive.config

import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@EnableConfigurationProperties(ArchiveProperties::class)
@Import(ArchiveShutdownConfiguration::class)
@Configuration
class ArchiveConfiguration {

    @Bean
    fun httpAuthSecurity(): HttpAuthSecurity {
        return HttpAuthSecurity().withPrefix("/archive")
    }
}
