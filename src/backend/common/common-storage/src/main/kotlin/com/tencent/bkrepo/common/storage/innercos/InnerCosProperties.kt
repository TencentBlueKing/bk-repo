package com.tencent.bkrepo.common.storage.innercos

import com.tencent.bkrepo.common.storage.core.ClientCredentials
import com.tencent.bkrepo.common.storage.core.StorageProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * inner cos 配置属性
 *
 * @author: carrypan
 * @date: 2019-09-16
 */
@ConfigurationProperties("storage.innercos")
class InnerCosProperties : StorageProperties() {

    var enabled: Boolean = false

    @NestedConfigurationProperty
    override var credentials: ClientCredentials = InnerCosCredentials()
}
