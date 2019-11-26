package com.tencent.bkrepo.common.artifact.repository.configuration

/**
 * 远程仓库 身份认证配置
 * @author: carrypan
 * @date: 2019/11/26
 */
data class RemoteCredentialsConfiguration(
    var username: String,
    var password: String
)