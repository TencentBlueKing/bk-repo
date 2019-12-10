package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.configuration.RepositoryConfiguration

/**
 * 构件下载context
 * @author: carrypan
 * @date: 2019/11/26
 */
class ArtifactDownloadContext : ArtifactTransferContext() {

    fun copy(repositoryInfo: RepositoryInfo): ArtifactDownloadContext {
        val context = ArtifactDownloadContext()
        context.repositoryInfo = repositoryInfo
        context.storageCredentials = CredentialsUtils.readString(repositoryInfo.storageCredentials?.type, repositoryInfo.storageCredentials?.credentials)
        context.repositoryConfiguration = JsonUtils.objectMapper.readValue(repositoryInfo.configuration, RepositoryConfiguration::class.java)
        context.contextAttributes = contextAttributes
        return context
    }
}
