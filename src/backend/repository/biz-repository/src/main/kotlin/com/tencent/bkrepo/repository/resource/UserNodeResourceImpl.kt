package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserNodeResource
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 用户节点接口实现类
 */
@RestController
class UserNodeResourceImpl @Autowired constructor(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val permissionManager: PermissionManager
) : UserNodeResource {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun detail(userId: String, artifactInfo: ArtifactInfo): Response<NodeDetail> {
        with(artifactInfo) {
            val node = nodeService.detail(projectId, repoName, artifactUri)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, artifactUri)
            return ResponseBuilder.success(node)
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    override fun mkdir(userId: String, artifactInfo: ArtifactInfo): Response<Void> {
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

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    override fun delete(userId: String, artifactInfo: ArtifactInfo): Response<Void> {
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

    override fun update(userId: String, request: UserNodeUpdateRequest): Response<Void> {
        with(request) {
            permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName)
            val updateRequest = NodeUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                expires = expires,
                operator = userId
            )
            nodeService.update(updateRequest)
            return ResponseBuilder.success()
        }
    }

    override fun rename(userId: String, request: UserNodeRenameRequest): Response<Void> {
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

    override fun move(userId: String, request: UserNodeMoveRequest): Response<Void> {
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

    override fun copy(userId: String, request: UserNodeCopyRequest): Response<Void> {
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

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun computeSize(userId: String, artifactInfo: ArtifactInfo): Response<NodeSizeInfo> {
        with(artifactInfo) {
            val nodeSizeInfo = nodeService.computeSize(projectId, repoName, artifactUri)
            return ResponseBuilder.success(nodeSizeInfo)
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun page(
        userId: String,
        artifactInfo: ArtifactInfo,
        page: Int,
        size: Int,
        includeFolder: Boolean,
        includeMetadata: Boolean,
        deep: Boolean
    ): Response<Page<NodeInfo>> {
        with(artifactInfo) {
            val nodePage = nodeService.page(projectId, repoName, artifactUri, page, size, includeFolder, includeMetadata, deep)
            return ResponseBuilder.success(nodePage)
        }
    }

    override fun query(userId: String, queryModel: QueryModel): Response<Page<Map<String, Any>>> {
        // 由于涉及到queryModel校验和解析规则，自定义查询在service内部鉴权
        return ResponseBuilder.success(nodeQueryService.userQuery(userId, queryModel))
    }
}
