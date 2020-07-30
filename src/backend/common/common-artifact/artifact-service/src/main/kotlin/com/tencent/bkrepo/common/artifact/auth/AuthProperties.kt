package com.tencent.bkrepo.common.artifact.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 *
 * @author: carrypan
 * @date: 2019/12/22
 */
@ConfigurationProperties("auth")
data class AuthProperties (
    var enabled: Boolean = true,
    var jwt: JwtProperties = JwtProperties()
) {
    data class JwtProperties (
        var secretKey: String = "secret@key",
        var expiration: Duration = Duration.ZERO
    )
}
