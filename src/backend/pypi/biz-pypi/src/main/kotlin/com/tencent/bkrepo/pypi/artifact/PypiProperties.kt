package com.tencent.bkrepo.pypi.artifact

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pypi")
data class PypiProperties(
    var domain: String = "http://127.0.0.1:25805"
)