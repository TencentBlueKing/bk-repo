package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "repository.metrics.push")
data class MetricsPushProperties(
    val enabled: Boolean = true,
    val intervalSeconds: Long = 60L,
    /** 用户白名单：全局关闭时仍允许上报，用于灰度验证 */
    val enabledUsers: Set<String> = emptySet(),
    /** 用户黑名单：全局开启时禁止上报，优先级高于 enabledUsers */
    val disabledUsers: Set<String> = emptySet(),
) {
    fun isEnabledForUser(userId: String): Boolean = when {
        userId in disabledUsers -> false
        userId in enabledUsers -> true
        else -> enabled
    }
}
