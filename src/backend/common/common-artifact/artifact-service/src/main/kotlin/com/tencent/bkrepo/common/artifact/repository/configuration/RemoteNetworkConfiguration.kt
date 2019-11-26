package com.tencent.bkrepo.common.artifact.repository.configuration

/**
 * 远程仓库 网络配置
 * @author: carrypan
 * @date: 2019/11/26
 */
data class RemoteNetworkConfiguration(
    val proxy: ProxyConfiguration? = null,
    val useSystemProxy: Boolean = false,
    val timeout: Long = 10*1000
)

data class ProxyConfiguration(
    val username: String,
    val password: String,
    val port: Int
)