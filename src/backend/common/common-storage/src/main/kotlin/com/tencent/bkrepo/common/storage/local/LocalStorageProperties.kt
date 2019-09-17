package com.tencent.bkrepo.common.storage.local

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * inner cos 配置属性
 *
 * @author: carrypan
 * @date: 2019-09-16
 */
@ConfigurationProperties("storage.local")
class LocalStorageProperties {

    var enabled: Boolean = false

    lateinit var directory: String

}