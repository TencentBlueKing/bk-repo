package com.tencent.bkrepo.common.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 *
 * @author: carrypan
 * @date: 2019/12/22
 */
@ConfigurationProperties("auth")
data class AuthProperties(var enabled: Boolean = true)
