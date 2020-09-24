package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import org.springframework.stereotype.Service

@Service
class MavenService{

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun deploy(
            mavenArtifactInfo: MavenArtifactInfo,
            file: ArtifactFile
    ) {
        val context = ArtifactUploadContext(file)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.upload(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun dependency(mavenArtifactInfo: MavenArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.download(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun delete(mavenArtifactInfo: MavenArtifactInfo): String {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.remove(context)
        return mavenArtifactInfo.getArtifactFullPath()
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun listVersion(mavenArtifactInfo: MavenArtifactInfo): List<Any> {
        val context = ArtifactSearchContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return repository.search(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun artifactDetail(mavenArtifactInfo: MavenArtifactInfo): Any? {
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return repository.query(context)
    }
}
