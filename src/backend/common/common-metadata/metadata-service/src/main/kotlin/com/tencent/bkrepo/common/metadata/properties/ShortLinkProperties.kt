package com.tencent.bkrepo.common.metadata.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 短链接配置
 */
@ConfigurationProperties(prefix = "shortlink")
data class ShortLinkProperties(
    /**
     * 对外域名，用于拼接 shortUrl，例如 `bkrepo.example.com` 或 `https://bkrepo.example.com`
     */
    var publicHost: String = "",
    /**
     * 允许的绝对 URL host 白名单；为空时仅允许相对路径
     */
    var allowedHosts: MutableList<String> = mutableListOf(),
    /**
     * 默认 TTL（天）
     */
    var defaultTtlDays: Long = 30,
    /**
     * 最大 TTL（天）
     */
    var maxTtlDays: Long = 365,
)
