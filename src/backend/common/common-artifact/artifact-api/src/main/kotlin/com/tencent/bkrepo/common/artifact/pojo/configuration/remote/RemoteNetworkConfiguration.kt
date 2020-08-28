package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

/**
 * 远程仓库 网络配置
 */
data class RemoteNetworkConfiguration(
    /**
     * 代理配置
     */
    val proxy: NetworkProxyConfiguration? = null,
    /**
     * 远程请求连接超时时间，单位ms
     */
    var connectTimeout: Long = 10 * 1000L,
    /**
     * 远程请求读超时时间，单位ms
     */
    var readTimeout: Long = 10 * 1000L
)

data class NetworkProxyConfiguration(
    var host: String,
    var port: Int,
    var username: String? = null,
    var password: String? = null
)
