package com.tencent.bkrepo.pypi.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.repository.PypiLocalRepository
import com.tencent.bkrepo.pypi.artifact.repository.PypiRepository
import com.tencent.bkrepo.pypi.pojo.PypiMigrateResponse
import org.springframework.stereotype.Service

@Service
class PypiService {

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun packages(pypiArtifactInfo: PypiArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun simple(artifactInfo: PypiArtifactInfo) {
        val context = ArtifactListContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.list(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun search(
        pypiArtifactInfo: PypiArtifactInfo,
        xmlString: String
    ) {
        val context = ArtifactSearchContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        (repository as PypiRepository).searchXml(context, xmlString)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun upload(
        pypiArtifactInfo: PypiArtifactInfo,
        artifactFileMap: ArtifactFileMap
    ) {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.upload(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun migrate(pypiArtifactInfo: PypiArtifactInfo): PypiMigrateResponse<String> {
        val context = ArtifactMigrateContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return (repository as PypiLocalRepository).migrateData(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun migrateResult(pypiArtifactInfo: PypiArtifactInfo): PypiMigrateResponse<String> {
        val context = ArtifactMigrateContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return (repository as PypiLocalRepository).migrateResult(context)
    }
}
