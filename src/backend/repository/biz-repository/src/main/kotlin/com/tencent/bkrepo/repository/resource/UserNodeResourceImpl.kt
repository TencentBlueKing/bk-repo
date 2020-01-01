package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.api.UserNodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeRenameRequest
import com.tencent.bkrepo.repository.service.NodeService
import com.tencent.bkrepo.repository.service.query.NodeQueryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 用户节点接口实现类
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
@RestController
class UserNodeResourceImpl @Autowired constructor(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val permissionService: PermissionService
) : UserNodeResource {

    override fun detail(userId: String, artifactInfo: ArtifactInfo): Response<NodeDetail?> {
        with(artifactInfo) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            val nodeDetail = nodeService.detail(projectId, repoName, this.artifactUri) ?: throw ErrorCodeException(
                ArtifactMessageCode.NODE_NOT_FOUND, this.artifactUri)
            return Response.success(nodeDetail)
        }
    }

    override fun mkdir(userId: String, artifactInfo: ArtifactInfo): Response<Void> {
        with(artifactInfo) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            val createRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = true,
                fullPath = this.artifactUri,
                overwrite = false,
                operator = userId
            )
            nodeService.create(createRequest)
            return Response.success()
        }
    }

    override fun delete(userId: String, artifactInfo: ArtifactInfo): Response<Void> {
        with(artifactInfo) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
            val deleteRequest = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = this.artifactUri,
                operator = userId
            )
            nodeService.delete(deleteRequest)
            return Response.success()
        }
    }

    override fun rename(userId: String, request: UserNodeRenameRequest): Response<Void> {
        with(request) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
            val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = newFullPath,
                operator = userId
            )
            nodeService.rename(renameRequest)
            return Response.success()
        }
    }

    override fun move(userId: String, request: UserNodeMoveRequest): Response<Void> {
        with(request) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, srcProjectId, srcRepoName))
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, destProjectId, destRepoName))
            val moveRequest = NodeMoveRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destPath = destPath,
                overwrite = overwrite,
                operator = userId
            )
            nodeService.move(moveRequest)
            return Response.success()
        }
    }

    override fun copy(userId: String, request: UserNodeCopyRequest): Response<Void> {
        with(request) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, srcProjectId, srcRepoName))
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, destProjectId, destRepoName))
            val copyRequest = NodeCopyRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destPath = destPath,
                overwrite = overwrite,
                operator = userId
            )
            nodeService.copy(copyRequest)
            return Response.success()
        }
    }

    override fun computeSize(userId: String, artifactInfo: ArtifactInfo): Response<NodeSizeInfo> {
        with(artifactInfo) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            val nodeSizeInfo = nodeService.computeSize(projectId, repoName, artifactUri)
            return Response.success(nodeSizeInfo)
        }
    }

    override fun list(userId: String, artifactInfo: ArtifactInfo, includeFolder: Boolean, deep: Boolean): Response<List<NodeInfo>> {
        with(artifactInfo) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            return Response.success(nodeService.list(projectId, repoName, artifactUri, includeFolder, deep))
        }
    }

    override fun query(userId: String, queryModel: QueryModel): Response<Page<Map<String, Any>>> {
        // 由于涉及到queryModel校验和解析规则，自定义查询在service内部鉴权
        return Response.success(nodeQueryService.userQuery(userId, queryModel))
    }
}
