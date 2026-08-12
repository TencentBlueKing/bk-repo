package com.tencent.bkrepo.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TOF 组织查询配置。
 *
 * ```yaml
 * tof:
 *   host:
 *   app-code:
 *   app-secret:
 * ```
 */
@ConfigurationProperties("tof")
data class TofProperties(
    var host: String = "",
    var appCode: String = "",
    var appSecret: String = "",
)
