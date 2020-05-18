package com.tencent.bkrepo.common.service.ribbon

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ribbon.gray")
data class RibbonGrayProperties(
    var enabled: Boolean = false,
    var localPrior: Boolean = false
)