package com.tencent.bkrepo.common.service.async

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("async")
data class AsyncProperties (
    val corePoolSize: Int = 100,
    val maxPoolSize: Int = 1000,
    val queueCapacity: Int = 1000,
    val keepAliveSeconds: Int = 60,
    val threadNamePrefix: String = "async-executor-"
)
