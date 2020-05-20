package com.tencent.bkrepo.common.notify.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 通知服务配置
 */
@ConfigurationProperties("notify")
data class NotifyProperties(
    /**
     * 蓝盾服务器地址
     */
    @NestedConfigurationProperty
    var devopsServer: String = ""
)
