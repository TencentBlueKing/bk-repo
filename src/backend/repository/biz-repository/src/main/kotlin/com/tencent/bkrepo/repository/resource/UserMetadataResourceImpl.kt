package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserMetadataResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataSaveRequest
import com.tencent.bkrepo.repository.service.MetadataService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据接口实现类
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@RestController
class UserMetadataResourceImpl @Autowired constructor(
    private val metadataService: MetadataService
) : UserMetadataResource {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun query(userId: String, artifactInfo: ArtifactInfo): Response<Map<String, String>> {
        artifactInfo.run {
            return ResponseBuilder.success(metadataService.query(projectId, repoName, artifactUri))
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    override fun save(userId: String, artifactInfo: ArtifactInfo, metadataSaveRequest: UserMetadataSaveRequest): Response<Void> {
        artifactInfo.run {
            val request = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactUri,
                metadata = metadataSaveRequest.metadata
            )
            metadataService.save(request)
            return ResponseBuilder.success()
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    override fun delete(userId: String, artifactInfo: ArtifactInfo, metadataDeleteRequest: UserMetadataDeleteRequest): Response<Void> {
        artifactInfo.run {
            val request = MetadataDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactUri,
                keyList = metadataDeleteRequest.keyList
            )
            metadataService.delete(request)
            return ResponseBuilder.success()
        }
    }
}
