package com.tencent.bkrepo.nuget.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NugetResourceService {

    @Autowired
    lateinit var repositoryClient: RepositoryClient

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun push(artifactInfo: ArtifactInfo, artifactFile: ArtifactFile) {
        val context = ArtifactUploadContext(artifactFile)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.upload(context)
    }
}
