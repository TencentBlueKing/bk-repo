package com.tencent.bkrepo.pypi.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.pypi.artifact.PackagesArtifactInfo
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Service
class PypiService {

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun download(artifactInfo: PypiArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun packages(artifactInfo: PackagesArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun simple(artifactInfo: PypiArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun upload(pypiArtifactInfo: PypiArtifactInfo,
        artifactFileMap: ArtifactFileMap
    )
    {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
    }

    companion object{
        private val logger = LoggerFactory.getLogger(PypiService::class.java)
    }
}
