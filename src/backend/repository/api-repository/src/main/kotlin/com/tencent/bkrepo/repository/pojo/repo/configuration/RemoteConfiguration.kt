package com.tencent.bkrepo.repository.pojo.repo.configuration

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
