package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChartManipulationService {

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap) {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
    }
}
