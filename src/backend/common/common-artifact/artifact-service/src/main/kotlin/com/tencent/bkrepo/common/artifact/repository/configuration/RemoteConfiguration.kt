package com.tencent.bkrepo.common.artifact.repository.configuration

import com.tencent.bkrepo.repository.pojo.repo.RepositoryConfiguration

/**
 * 远程仓库配置
 * @author: carrypan
 * @date: 2019/11/26
 */
open class RemoteConfiguration(
    val url: String,
    val credentialsConfiguration: RemoteCredentialsConfiguration? = null,
    val networkConfiguration: RemoteNetworkConfiguration,
    val cacheConfiguration: RemoteCacheConfiguration
): RepositoryConfiguration