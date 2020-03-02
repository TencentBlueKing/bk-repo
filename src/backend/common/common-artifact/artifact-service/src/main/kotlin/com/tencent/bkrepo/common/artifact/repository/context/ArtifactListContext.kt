package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/**
 * 构件列表context
 * @author: carrypan
 * @date: 2019/11/26
 */
class ArtifactListContext : ArtifactTransferContext(){

    fun copy(repositoryInfo: RepositoryInfo): ArtifactListContext {
        val context = ArtifactListContext()
        context.repositoryInfo = repositoryInfo
        context.storageCredentials = repositoryInfo.storageCredentials
        context.repositoryConfiguration = repositoryInfo.configuration
        context.contextAttributes = contextAttributes
        return context
    }
}
