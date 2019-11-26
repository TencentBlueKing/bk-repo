package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable.Companion.ARTIFACT_COORDINATE_URI
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataSaveRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 节点元数据服务接口
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Api("节点元数据服务接口")
@RequestMapping("/api/metadata")
interface UserMetadataResource {
    @ApiOperation("查询元数据列表")
    @GetMapping(ARTIFACT_COORDINATE_URI)
    fun query(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<Map<String, String>>

    @ApiOperation("创建/更新元数据列表")
    @PostMapping(ARTIFACT_COORDINATE_URI)
    fun save(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo,
        @RequestBody
        metadataSaveRequest: UserMetadataSaveRequest
    ): Response<Void>

    @ApiOperation("删除元数据")
    @DeleteMapping(ARTIFACT_COORDINATE_URI)
    fun delete(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo,
        @RequestBody
        metadataDeleteRequest: UserMetadataDeleteRequest
    ): Response<Void>
}
