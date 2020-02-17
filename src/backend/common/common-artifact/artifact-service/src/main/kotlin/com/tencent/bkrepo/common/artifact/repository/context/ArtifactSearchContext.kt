package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/**
 * 构件搜索context
 * @author: carrypan
 * @date: 2019/11/26
 */
class ArtifactSearchContext : ArtifactTransferContext(){
    fun copy(repositoryInfo: RepositoryInfo): ArtifactSearchContext {
        val context = ArtifactSearchContext()
        context.repositoryInfo = repositoryInfo
        context.storageCredentials = repositoryInfo.storageCredentials
        context.repositoryConfiguration = repositoryInfo.configuration
        context.contextAttributes = contextAttributes
        return context
    }
}
