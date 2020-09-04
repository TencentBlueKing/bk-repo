package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

/**
 * 远程仓库代理配置
 */
data class RemoteCredentialsConfiguration(
    /**
     * 用户名
     */
    var username: String? = null,
    /**
     * 密码
     */
    var password: String? = null
)
