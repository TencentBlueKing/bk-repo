package com.tencent.bkrepo.common.storage.innercos

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * inner cos 配置属性
 *
 * @author: carrypan
 * @date: 2019-09-16
 */
@ConfigurationProperties("storage.innercos")
class InnerCosProperties {

    var enabled: Boolean = false

    lateinit var credentials: InnerCosCredentials
}