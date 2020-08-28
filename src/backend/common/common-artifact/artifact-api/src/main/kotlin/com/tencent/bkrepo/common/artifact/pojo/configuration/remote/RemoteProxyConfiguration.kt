package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

/**
 * 远程仓库代理配置
 */
data class RemoteProxyConfiguration(
    /**
     * 远程仓库url
     */
    val url: String,
    /**
     * 用户名
     */
    val username: String? = null,
    /**
     * 密码
     */
    val password: String? = null
)
