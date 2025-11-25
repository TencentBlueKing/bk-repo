package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("repository.drive")
data class DriveProperties(
    /**
     * ci 服务器地址
     */
    var ciServer: String = "",
    /**
     * ci experience 服务认证token
     */
    var ciToken: String = "",
    /**
     * bkdirve 灰度标识
     */
    var gray: String = "",
)
