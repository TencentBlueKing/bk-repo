package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import org.springframework.stereotype.Service

@Service
class RpmWebService {

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deletePackage(rpmArtifactInfo: RpmArtifactInfo, packageKey: String) {
        // 先查出所有版本，deleteIndex时一次性删除所有版本。
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun delete(rpmArtifactInfo: RpmArtifactInfo, packageKey: String, version: String?) {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun artifactDetail(rpmArtifactInfo: RpmArtifactInfo, packageKey: String, version: String?): Any? {
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return repository.query(context)
    }
}
