package com.tencent.bkrepo.pypi.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import org.springframework.stereotype.Service

@Service
class PypiWebService {

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deletePackage(pypiArtifactInfo: PypiArtifactInfo, packageKey: String) {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun delete(pypiArtifactInfo: PypiArtifactInfo, packageKey: String, version: String?) {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun artifactDetail(pypiArtifactInfo: PypiArtifactInfo, packageKey: String, version: String?): Any? {
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return repository.query(context)
    }
}
