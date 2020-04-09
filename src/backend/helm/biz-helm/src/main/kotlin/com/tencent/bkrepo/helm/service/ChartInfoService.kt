package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.repository.HelmLocalRepository
import org.springframework.stereotype.Service

@Service
class ChartInfoService {
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun allChartsList(artifactInfo: HelmArtifactInfo): String {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        return (repository as HelmLocalRepository).searchJson(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun isExists(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        (repository as HelmLocalRepository).isExists(context)
    }
}
