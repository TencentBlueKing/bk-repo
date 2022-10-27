package com.tencent.bkrepo.git.service

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.git.artifact.GitContentArtifactInfo
import com.tencent.bkrepo.git.artifact.GitRepositoryArtifactInfo
import com.tencent.bkrepo.git.internal.CodeRepositoryResolver
import com.tencent.bkrepo.git.listener.SyncRepositoryEvent
import com.tencent.bkrepo.git.context.UserHolder
import org.springframework.stereotype.Service

/**
 * Git USER API服务
 * */
@Service
class GitApiService : ArtifactService() {

    fun sync(infoRepository: GitRepositoryArtifactInfo) {
        val context = ArtifactDownloadContext()
        with(context) {
            val db = CodeRepositoryResolver.open(projectId, repoName, storageCredentials)
            val event = SyncRepositoryEvent(repositoryDetail, db, UserHolder.getUser())
            SpringContextUtils.publishEvent(event)
        }
    }

    fun getContent(gitContentArtifactInfo: GitContentArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }
}
