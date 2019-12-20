package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/**
 * 构件下载context
 * @author: carrypan
 * @date: 2019/11/26
 */
class ArtifactDownloadContext : ArtifactTransferContext() {

    fun copy(repositoryInfo: RepositoryInfo): ArtifactDownloadContext {
        val context = ArtifactDownloadContext()
        context.repositoryInfo = repositoryInfo
//      // context.storageCredentials = CredentialsUtils.readString(repositoryInfo.storageCredentials?.type, repositoryInfo.storageCredentials?.credentials)
        context.repositoryConfiguration = repositoryInfo.configuration
        context.contextAttributes = contextAttributes
        return context
    }
}
