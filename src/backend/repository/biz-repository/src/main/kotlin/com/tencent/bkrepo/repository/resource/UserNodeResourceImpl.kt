package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.locator.ArtifactLocation
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.api.UserNodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
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

    override fun detail(userId: String, artifactLocation: ArtifactLocation): Response<NodeDetail?> {
        artifactLocation.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            val nodeDetail = nodeService.detail(projectId, repoName, fullPath) ?: throw ErrorCodeException(
                CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
            return Response.success(nodeDetail)
        }
    }

    override fun mkdir(userId: String, artifactLocation: ArtifactLocation): Response<Void> {
        artifactLocation.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            val createRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = true,
                fullPath = fullPath,
                overwrite = false,
                operator = userId
            )
            nodeService.create(createRequest)
            return Response.success()
        }
    }

    override fun delete(userId: String, artifactLocation: ArtifactLocation): Response<Void> {
        artifactLocation.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
            val deleteRequest = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                operator = userId
            )
            nodeService.delete(deleteRequest)
            return Response.success()
        }
    }

    override fun rename(userId: String, request: UserNodeRenameRequest): Response<Void> {
        request.run {
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
        request.run {
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
        request.run {
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

    override fun computeSize(userId: String, artifactLocation: ArtifactLocation): Response<NodeSizeInfo> {
        artifactLocation.run {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            val nodeSizeInfo = nodeService.computeSize(projectId, repoName, fullPath)
            return Response.success(nodeSizeInfo)
        }
    }

    override fun query(userId: String, queryModel: QueryModel): Response<Page<Map<String, Any>>> {
        // 由于涉及到queryModel校验和解析规则，自定义查询在service内部鉴权
        return Response.success(nodeQueryService.userQuery(userId, queryModel))
    }
}
