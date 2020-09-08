package com.tencent.bkrepo.common.security.http

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("security.auth")
data class HttpAuthProperties(
    /**
     * 是否开启认证
     */
    var enabled: Boolean = true
)
