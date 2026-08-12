package com.tencent.bkrepo.preview.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 蓝鲸用户自定义部门关系配置。
 *
 * ```yaml
 * bk-user-custom:
 *   url:
 *   app-code:
 *   app-secret:
 * ```
 */
@ConfigurationProperties("bk-user-custom")
data class BKUserCustomProperties(
    var url: String = "",
    var appCode: String = "",
    var appSecret: String = "",
)
