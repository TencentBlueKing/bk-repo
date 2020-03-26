package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

/**
 * 远程仓库配置
 * @author: carrypan
 * @date: 2019/11/26
 */
open class RemoteConfiguration(
    val url: String,
    val credentialsConfiguration: RemoteCredentialsConfiguration? = null,
    val networkConfiguration: RemoteNetworkConfiguration = RemoteNetworkConfiguration(),
    val cacheConfiguration: RemoteCacheConfiguration = RemoteCacheConfiguration()
) : RepositoryConfiguration() {
    companion object {
        const val type = "remote"
    }
}
