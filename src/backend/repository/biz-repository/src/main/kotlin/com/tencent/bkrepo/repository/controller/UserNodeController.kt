package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeUpdateRequest
import com.tencent.bkrepo.repository.service.NodeService
import com.tencent.bkrepo.repository.service.query.NodeQueryService
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
import org.springframework.web.bind.annotation.RestController

@Api("节点用户接口")
@RestController
@RequestMapping("/api/node")
class UserNodeController(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val permissionManager: PermissionManager
) {

    @ApiOperation("根据路径查看节点详情")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping(DEFAULT_MAPPING_URI/* Deprecated */, "/detail/$DEFAULT_MAPPING_URI")
    fun detail(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<NodeDetail> {
        with(artifactInfo) {
            val node = nodeService.detail(projectId, repoName, artifactUri)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, artifactUri)
            return ResponseBuilder.success(node)
        }
    }

    @ApiOperation("创建文件夹")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping(DEFAULT_MAPPING_URI/* Deprecated */, "/mkdir/$DEFAULT_MAPPING_URI")
    fun mkdir(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<Void> {
        with(artifactInfo) {
            val createRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = true,
                fullPath = artifactUri,
                overwrite = false,
                operator = userId
            )
            nodeService.create(createRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除节点")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @DeleteMapping(DEFAULT_MAPPING_URI/* Deprecated */, "/delete/$DEFAULT_MAPPING_URI")
    fun delete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<Void> {
        with(artifactInfo) {
            val deleteRequest = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactUri,
                operator = userId
            )
            nodeService.delete(deleteRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("更新节点")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping("/update/$DEFAULT_MAPPING_URI")
    fun update(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody request: UserNodeUpdateRequest
    ): Response<Void> {
        with(artifactInfo) {
            val updateRequest = NodeUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactUri,
                expires = request.expires,
                operator = userId
            )
            nodeService.update(updateRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("重命名节点")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping( "/rename/$DEFAULT_MAPPING_URI")
    fun rename(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestParam newFullPath: String
    ): Response<Void> {
        with(artifactInfo) {
            val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactUri,
                newFullPath = newFullPath,
                operator = userId
            )
            nodeService.rename(renameRequest)
            return ResponseBuilder.success()
        }
    }

    @Deprecated("/rename/{projectId}/{repoName}/**")
    @ApiOperation("重命名节点")
    @PostMapping("/rename")
    fun rename(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeRenameRequest
    ): Response<Void> {
        with(request) {
            permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName)
            val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = newFullPath,
                operator = userId
            )
            nodeService.rename(renameRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("移动节点")
    @PostMapping("/move")
    fun move(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeMoveRequest
    ): Response<Void> {
        with(request) {
            permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, srcProjectId, srcRepoName)
            if (destProjectId != null && destRepoName != null) {
                permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, destProjectId!!, destRepoName!!)
            }
            val moveRequest = NodeMoveRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destFullPath = destFullPath,
                overwrite = overwrite,
                operator = userId
            )
            nodeService.move(moveRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("复制节点")
    @PostMapping("/copy")
    fun copy(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeCopyRequest
    ): Response<Void> {
        with(request) {
            permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, srcProjectId, srcRepoName)
            if (destProjectId != null && destRepoName != null) {
                permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, destProjectId!!, destRepoName!!)
            }
            val copyRequest = NodeCopyRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destFullPath = destFullPath,
                overwrite = overwrite,
                operator = userId
            )
            nodeService.copy(copyRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("查询节点大小信息")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/size/$DEFAULT_MAPPING_URI")
    fun computeSize(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<NodeSizeInfo> {
        with(artifactInfo) {
            val nodeSizeInfo = nodeService.computeSize(projectId, repoName, artifactUri)
            return ResponseBuilder.success(nodeSizeInfo)
        }
    }

    @ApiOperation("分页查询节点")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/page/$DEFAULT_MAPPING_URI")
    fun page(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam(value = "当前页", required = true, defaultValue = "0")
        @RequestParam page: Int = 0,
        @ApiParam(value = "分页大小", required = true, defaultValue = "20")
        @RequestParam size: Int = 20,
        @ApiParam("是否包含目录", required = false, defaultValue = "true")
        @RequestParam includeFolder: Boolean = true,
        @ApiParam("是否包含元数据", required = false, defaultValue = "false")
        @RequestParam includeMetadata: Boolean = false,
        @ApiParam("是否深度查询文件", required = false, defaultValue = "false")
        @RequestParam deep: Boolean = false
    ): Response<Page<NodeInfo>> {
        with(artifactInfo) {
            val nodePage = nodeService.page(projectId, repoName, artifactUri, page, size, includeFolder, includeMetadata, deep)
            return ResponseBuilder.success(nodePage)
        }
    }

    @ApiOperation("自定义查询节点")
    @PostMapping("/query")
    fun query(
        @RequestAttribute userId: String,
        @RequestBody queryModel: QueryModel
    ): Response<Page<Map<String, Any>>> {
        // 由于涉及到queryModel校验和解析规则，自定义查询在service内部鉴权
        return ResponseBuilder.success(nodeQueryService.userQuery(userId, queryModel))
    }
}
