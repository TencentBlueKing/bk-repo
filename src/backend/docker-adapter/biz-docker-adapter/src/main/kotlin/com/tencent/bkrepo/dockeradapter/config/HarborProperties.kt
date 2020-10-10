package com.tencent.bkrepo.dockeradapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("adapter.harbor")
data class HarborProperties(
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var imagePrefix: String = "secret@key"
)
