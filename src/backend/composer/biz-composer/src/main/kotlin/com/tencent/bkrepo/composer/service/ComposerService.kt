package com.tencent.bkrepo.composer.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.artifact.repository.ComposerRepository
import org.springframework.stereotype.Service

@Service
class ComposerService {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun installRequire(composerArtifactInfo: ComposerArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.download(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getJson(composerArtifactInfo: ComposerArtifactInfo): String? {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        return (repository as ComposerRepository).getJson(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun packages(composerArtifactInfo: ComposerArtifactInfo): String? {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        return (repository as ComposerRepository).packages(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun deploy(composerArtifactInfo: ComposerArtifactInfo, file: ArtifactFile) {
        val context = ArtifactUploadContext(file)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
    }
}
