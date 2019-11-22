package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable.Companion.ARTIFACT_COORDINATE_URI
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeRenameRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 用户节点服务接口
 *
 * @author: carrypan
 * @date: 2019-11-18
 */
@Api("用户节点服务接口")
@FeignClient(SERVICE_NAME, contextId = "UserNodeResource")
@RequestMapping("/user/node")
interface UserNodeResource {

    @ApiOperation("根据路径查看节点详情")
    @GetMapping(ARTIFACT_COORDINATE_URI)
    fun detail(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<NodeDetail?>

    @ApiOperation("创建文件夹")
    @PostMapping(ARTIFACT_COORDINATE_URI)
    fun mkdir(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<Void>

    @ApiOperation("删除节点")
    @DeleteMapping(ARTIFACT_COORDINATE_URI)
    fun delete(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<Void>

    @ApiOperation("重命名节点")
    @PutMapping("/rename")
    fun rename(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @RequestBody request: UserNodeRenameRequest
    ): Response<Void>

    @ApiOperation("移动节点")
    @PutMapping("/move")
    fun move(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @RequestBody request: UserNodeMoveRequest
    ): Response<Void>

    @ApiOperation("复制节点")
    @PutMapping("/copy")
    fun copy(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @RequestBody request: UserNodeCopyRequest
    ): Response<Void>

    @ApiOperation("查询节点大小信息")
    @GetMapping("/size/$ARTIFACT_COORDINATE_URI")
    fun computeSize(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<NodeSizeInfo>

    @ApiOperation("自定义查询节点")
    @PostMapping("/query")
    fun query(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @RequestBody queryModel: QueryModel
    ): Response<Page<Map<String, Any>>>
}
