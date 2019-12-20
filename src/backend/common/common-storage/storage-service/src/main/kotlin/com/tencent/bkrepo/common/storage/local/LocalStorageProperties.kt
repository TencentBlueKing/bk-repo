package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.pojo.LocalStorageCredentials
import com.tencent.bkrepo.common.storage.pojo.StorageCredentials
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * inner cos 配置属性
 *
 * @author: carrypan
 * @date: 2019-09-16
 */
@ConfigurationProperties("storage.local")
class LocalStorageProperties : StorageProperties() {

    @NestedConfigurationProperty
    override var credentials: StorageCredentials = LocalStorageCredentials()
}
