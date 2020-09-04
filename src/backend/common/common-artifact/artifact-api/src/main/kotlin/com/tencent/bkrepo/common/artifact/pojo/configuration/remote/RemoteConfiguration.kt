package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

/**
 * 远程仓库配置
 */
data class RemoteConfiguration(
    var url: String = "",
    var credentials: RemoteCredentialsConfiguration = RemoteCredentialsConfiguration(),
    var network: RemoteNetworkConfiguration = RemoteNetworkConfiguration(),
    var cache: RemoteCacheConfiguration = RemoteCacheConfiguration()
) : RepositoryConfiguration() {
    companion object {
        const val type = "remote"
    }
}
