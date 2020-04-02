package com.tencent.bkrepo.common.artifact.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 *
 * @author: carrypan
 * @date: 2019/12/22
 */
@ConfigurationProperties("auth")
class AuthProperties {
    var enabled: Boolean = true
    var jwt = JwtProperties()

    class JwtProperties {
        var secretKey: String = "bkrepo"
        var expireSeconds: Long = -1
    }
}



