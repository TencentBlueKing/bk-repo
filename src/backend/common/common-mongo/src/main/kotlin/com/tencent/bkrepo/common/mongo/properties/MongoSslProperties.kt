package com.tencent.bkrepo.common.mongo.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "tls.mongodb")
@ConstructorBinding
data class MongoSslProperties(
    /**
     * 连接mongo是否启用ssl
     */
    var enabled: Boolean = false,
    /**
     * 信任库地址，暂时以文件形式（运维提供）
     */
    var trustStore: String? = null,

    /**
     * 信任库密码
     */
    var trustStorePassword: String? = null,

    /**
     * 信任库类型，默认JKS
     */
    var trustStoreType: String = "JKS",

    var invalidHostnameAllowed: Boolean = false,

    /**
     * 客户端的证书地址，文件形式
     */
    var keyStore: String? = null,

    /**
     * 客户端证书存储密钥
     */
    var keyStorePassword: String? = null,

    /**
     * 客户端证书存储类型，默认JKS
     */
    var keyStoreType: String = "JKS",

    /**
     * 客户端密钥
     */
    var keyPassword: String? = null,
) {
    fun isMutualTlsConfigured(): Boolean {
        return !keyStore.isNullOrBlank()
    }
}
