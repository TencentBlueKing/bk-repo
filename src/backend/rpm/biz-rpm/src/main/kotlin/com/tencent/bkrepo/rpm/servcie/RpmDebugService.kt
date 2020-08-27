package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import org.springframework.stereotype.Service
import org.springframework.web.HttpRequestHandler

@Service
class RpmDebugService {
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun flushRepomd(rpmArtifactInfo: RpmArtifactInfo) {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        (repository as RpmLocalRepository).flushRepoMdXML(context)
    }
}