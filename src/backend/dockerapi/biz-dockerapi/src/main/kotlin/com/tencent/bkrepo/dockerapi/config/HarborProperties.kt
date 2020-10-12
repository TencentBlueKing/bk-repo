package com.tencent.bkrepo.dockerapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dockerapi.harbor")
data class HarborProperties(
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var imagePrefix: String = ""
)
