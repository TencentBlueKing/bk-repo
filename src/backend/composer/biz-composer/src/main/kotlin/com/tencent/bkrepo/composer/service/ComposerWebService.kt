package com.tencent.bkrepo.composer.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import org.springframework.stereotype.Service

@Service
class ComposerWebService {

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deletePackage(composerArtifactInfo: ComposerArtifactInfo, packageKey: String) {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun delete(composerArtifactInfo: ComposerArtifactInfo, packageKey: String, version: String?) {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun artifactDetail(composerArtifactInfo: ComposerArtifactInfo, packageKey: String, version: String?): Any? {
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return repository.query(context)
    }
}