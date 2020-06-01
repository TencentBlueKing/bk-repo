package com.tencent.bkrepo.npm.async

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("npm.async")
data class NpmAsyncProperties(
    var corePoolSize: Int = 100,
    var maxPoolSize: Int = 1000,
    var queueCapacity: Int = 1000,
    var keepAliveSeconds: Int = 60,
    var threadNamePrefix: String = "npm-async-executor-"
)
