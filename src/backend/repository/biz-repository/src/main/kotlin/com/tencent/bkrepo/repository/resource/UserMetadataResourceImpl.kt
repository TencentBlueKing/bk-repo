package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
import com.tencent.bkrepo.common.auth.PermissionService
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
    private val permissionService: PermissionService,
    private val metadataService: MetadataService
) : UserMetadataResource {
    override fun query(userId: String, artifactCoordinate: ArtifactCoordinate): Response<Map<String, String>> {
        artifactCoordinate.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            return Response.success(metadataService.query(projectId, repoName, artifactPath.fullPath))
        }
    }

    override fun save(userId: String, artifactCoordinate: ArtifactCoordinate, metadataSaveRequest: UserMetadataSaveRequest): Response<Void> {
        artifactCoordinate.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
            val request = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactPath.fullPath,
                metadata = metadataSaveRequest.metadata
            )
            metadataService.save(request)
            return Response.success()
        }
    }

    override fun delete(userId: String, artifactCoordinate: ArtifactCoordinate, metadataDeleteRequest: UserMetadataDeleteRequest): Response<Void> {
        artifactCoordinate.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
            val request = MetadataDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactPath.fullPath,
                keyList = metadataDeleteRequest.keyList
            )
            metadataService.delete(request)
            return Response.success()
        }
    }
}
