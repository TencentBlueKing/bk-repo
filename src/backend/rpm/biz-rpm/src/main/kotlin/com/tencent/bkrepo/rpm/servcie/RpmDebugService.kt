package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import org.springframework.stereotype.Service

@Service
class RpmDebugService {
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun flushRepomd(rpmArtifactInfo: RpmArtifactInfo) {
        val context = ArtifactSearchContext()
        (ArtifactContextHolder.getRepository() as RpmLocalRepository).flushRepoMdXML(context, null)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun flushAllRepomd(rpmArtifactInfo: RpmArtifactInfo) {
        val context = ArtifactContext()
        (ArtifactContextHolder.getRepository() as RpmLocalRepository).flushAllRepoData(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun delete(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo): Response<Void> {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository()
        repository.remove(context)
        return ResponseBuilder.success()
    }
}
