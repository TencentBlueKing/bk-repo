package com.tencent.bkrepo.common.lock.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.redis.core.RedisOperations

@ConditionalOnClass(RedisOperations::class)
@ConfigurationProperties(prefix = "spring.redis")
data class RedisConfigProperties(
    var host: String? = null,
    var password: String = "",
    var port: Int = 6379,
    var ssl: Boolean = false,
    var clientName: String = "",
    var cluster: RedisProperties.Cluster? = null
)
