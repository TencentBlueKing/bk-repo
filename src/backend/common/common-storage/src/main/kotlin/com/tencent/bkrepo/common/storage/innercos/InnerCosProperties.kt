package com.tencent.bkrepo.common.storage.innercos

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * inner cos 配置属性
 *
 * @author: carrypan
 * @date: 2019-09-16
 */
@ConfigurationProperties("storage.innercos")
class InnerCosProperties {

    var enabled: Boolean = false

    @NestedConfigurationProperty
    var credentials: InnerCosCredentials = InnerCosCredentials()
}