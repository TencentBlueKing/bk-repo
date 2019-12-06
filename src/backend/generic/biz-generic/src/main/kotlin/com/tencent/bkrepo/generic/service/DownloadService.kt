package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import org.springframework.stereotype.Service

/**
 * 通用文件下载服务类
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@Service
class DownloadService {

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun download(artifactInfo: GenericArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.download(context)
    }
}
