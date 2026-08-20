package com.tencent.bkrepo.preview.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 作品分享配置。
 *
 * ```yaml
 * preview:
 *   artifact-share:
 *     domain:
 * ```
 */
@ConfigurationProperties("preview.artifact-share")
data class ArtifactShareProperties(
    var domain: String = "",
)
