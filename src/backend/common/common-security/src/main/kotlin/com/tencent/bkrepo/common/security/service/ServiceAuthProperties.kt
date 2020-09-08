package com.tencent.bkrepo.common.security.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("security.service")
data class ServiceAuthProperties(
    var enabled: Boolean = true,
    var secretKey: String = "secret@key"
)
