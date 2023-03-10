package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteCredentialsConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteNetworkConfiguration

data class RemoteUrlRequest(
    val type: String? = null,
    val url: String,
    val credentials: RemoteCredentialsConfiguration = RemoteCredentialsConfiguration(),
    val network: RemoteNetworkConfiguration = RemoteNetworkConfiguration()
)
