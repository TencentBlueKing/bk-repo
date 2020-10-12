package com.tencent.bkrepo.dockeradapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("docker-adapter.bkrepo")
data class BkRepoProperties(
    var url: String = "",
    var authorization: String = "",
    var domain: String = ""
)
