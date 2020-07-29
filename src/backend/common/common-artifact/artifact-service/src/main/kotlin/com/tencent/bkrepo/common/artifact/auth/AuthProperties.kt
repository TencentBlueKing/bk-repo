package com.tencent.bkrepo.common.artifact.auth

import org.springframework.boot.context.properties.ConfigurationProperties

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
        var secretKey: String = "bkrepo",
        var expireSeconds: Long = -1
    )
}
