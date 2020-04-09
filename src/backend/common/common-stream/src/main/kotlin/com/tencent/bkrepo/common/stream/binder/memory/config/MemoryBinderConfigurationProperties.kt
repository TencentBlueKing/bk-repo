package com.tencent.bkrepo.common.stream.binder.memory.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.cloud.stream.memory.binder")
data class MemoryBinderConfigurationProperties(
    var workerPoolSize: Int = -1,
    var queueSize: Int = 2048
)
