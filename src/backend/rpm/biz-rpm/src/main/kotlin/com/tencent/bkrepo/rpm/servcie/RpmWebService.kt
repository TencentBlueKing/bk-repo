package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import org.springframework.stereotype.Service

@Service
class RpmWebService {

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun delete(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo) {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository()
        repository.remove(context)
    }
}
