package com.tencent.bkrepo.common.security.http.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("security.auth.jwt")
data class JwtAuthProperties(
    var secretKey: String = "secret@key",
    var expiration: Duration = Duration.ZERO
)
