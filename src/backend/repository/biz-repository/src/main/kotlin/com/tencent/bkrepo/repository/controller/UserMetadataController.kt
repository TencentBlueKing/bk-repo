package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataSaveRequest
import com.tencent.bkrepo.repository.service.MetadataService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据接口实现类
 */
@Api("节点元数据用户接口")
@RestController
@RequestMapping("/api/metadata")
class UserMetadataController(
    private val metadataService: MetadataService
) {

    @ApiOperation("查询元数据列表")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping(DEFAULT_MAPPING_URI)
    fun query(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<Map<String, Any>> {
        artifactInfo.run {
            return ResponseBuilder.success(metadataService.query(projectId, repoName, getArtifactFullPath()))
        }
    }

    @ApiOperation("创建/更新元数据列表")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping(DEFAULT_MAPPING_URI)
    fun save(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody metadataSaveRequest: UserMetadataSaveRequest
    ): Response<Void> {
        artifactInfo.run {
            val request = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                metadata = metadataSaveRequest.metadata
            )
            metadataService.save(request)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除元数据")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @DeleteMapping(DEFAULT_MAPPING_URI)
    fun delete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody metadataDeleteRequest: UserMetadataDeleteRequest
    ): Response<Void> {
        artifactInfo.run {
            val request = MetadataDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                keyList = metadataDeleteRequest.keyList
            )
            metadataService.delete(request)
            return ResponseBuilder.success()
        }
    }
}
