package com.tencent.bkrepo.common.service.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("security")
data class SecurityProperties(
    var secretKey: String = "secret@key",
    var enabled: Boolean = true
)
