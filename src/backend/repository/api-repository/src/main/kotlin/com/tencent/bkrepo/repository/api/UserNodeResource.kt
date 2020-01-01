package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeRenameRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 用户节点服务接口
 *
 * @author: carrypan
 * @date: 2019-11-18
 */
@Api("用户节点服务接口")
@RequestMapping("/api/node")
interface UserNodeResource {

    @ApiOperation("根据路径查看节点详情")
    @GetMapping(DEFAULT_MAPPING_URI)
    fun detail(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<NodeDetail>

    @ApiOperation("创建文件夹")
    @PostMapping(DEFAULT_MAPPING_URI)
    fun mkdir(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<Void>

    @ApiOperation("删除节点")
    @DeleteMapping(DEFAULT_MAPPING_URI)
    fun delete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<Void>

    @ApiOperation("重命名节点")
    @PostMapping("/rename")
    fun rename(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeRenameRequest
    ): Response<Void>

    @ApiOperation("移动节点")
    @PostMapping("/move")
    fun move(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeMoveRequest
    ): Response<Void>

    @ApiOperation("复制节点")
    @PostMapping("/copy")
    fun copy(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeCopyRequest
    ): Response<Void>

    @ApiOperation("查询节点大小信息")
    @GetMapping("/size/$DEFAULT_MAPPING_URI")
    fun computeSize(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<NodeSizeInfo>

    @ApiOperation("列出目录下的文件")
    @GetMapping("/list/$DEFAULT_MAPPING_URI")
    fun list(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("是否包含目录", required = false, defaultValue = "false")
        @RequestParam includeFolder: Boolean = true,
        @ApiParam("是否深度查询文件", required = false, defaultValue = "false")
        @RequestParam deep: Boolean = false
    ): Response<List<NodeInfo>>

    @ApiOperation("自定义查询节点")
    @PostMapping("/query")
    fun query(
        @RequestAttribute userId: String,
        @RequestBody queryModel: QueryModel
    ): Response<Page<Map<String, Any>>>
}
