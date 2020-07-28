package com.tencent.bkrepo.common.service.async

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("async")
data class AsyncProperties(
    var corePoolSize: Int = 100,
    var maxPoolSize: Int = 1000,
    var queueCapacity: Int = 1000,
    var keepAliveSeconds: Int = 60,
    var threadNamePrefix: String = "async-executor-"
)
