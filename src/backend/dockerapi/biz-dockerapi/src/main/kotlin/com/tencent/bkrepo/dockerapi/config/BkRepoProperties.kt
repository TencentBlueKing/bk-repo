package com.tencent.bkrepo.dockerapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dockerapi.bkrepo")
data class BkRepoProperties(
    var url: String = "",
    var authorization: String = "",
    var domain: String = ""
)
