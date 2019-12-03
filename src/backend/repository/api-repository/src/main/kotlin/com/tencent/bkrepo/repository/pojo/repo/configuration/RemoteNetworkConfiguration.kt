package com.tencent.bkrepo.repository.pojo.repo.configuration

/**
 * 远程仓库 网络配置
 * @author: carrypan
 * @date: 2019/11/26
 */
data class RemoteNetworkConfiguration(
    /**
     * 代理配置
     */
    val proxy: ProxyConfiguration? = null,
    /**
     * 远程请求请求超时时间，单位ms
     */
    val timeout: Long = 10*1000
)

data class ProxyConfiguration(
    val username: String,
    val password: String,
    val port: Int
)