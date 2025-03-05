package com.tencent.bkrepo.common.artifact.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("multitenant")
data class EnableMultiTenantProperties(
    var enabled: Boolean = false
)
