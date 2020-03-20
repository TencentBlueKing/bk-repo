package com.tencent.bkrepo.common.service.ribbon

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ribbon.route.rule")
data class RibbonRouteRuleProperties (
    val enabled: Boolean = true
)