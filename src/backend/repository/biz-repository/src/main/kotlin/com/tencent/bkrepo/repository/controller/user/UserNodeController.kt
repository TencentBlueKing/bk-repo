/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_DELETE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.artifact.audit.NODE_VIEW_ACTION
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.pojo.node.NodeRestoreOption
import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDeletedPoint
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRestoreRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeArchiveRestoreRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.software.ProjectPackageOverview
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(name = "节点用户接口")
@RestController
@RequestMapping("/api/node")
class UserNodeController(
    private val nodeService: NodeService,
    private val nodeSearchService: NodeSearchService,
    private val permissionManager: PermissionManager,
) {

    @AuditEntry(
        actionId = NODE_VIEW_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_VIEW_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_VIEW_CONTENT
    )
    @Operation(summary = "根据路径查看节点详情")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping(DEFAULT_MAPPING_URI/* Deprecated */, "/detail/$DEFAULT_MAPPING_URI")
    fun getNodeDetail(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
    ): Response<NodeDetail> {
        val node = nodeService.getNodeDetail(artifactInfo)
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, artifactInfo.getArtifactFullPath())
        return ResponseBuilder.success(node)
    }

    @AuditEntry(
        actionId = NODE_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_CREATE_CONTENT
    )
    @Operation(summary = "创建文件夹")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping(DEFAULT_MAPPING_URI/* Deprecated */, "/mkdir/$DEFAULT_MAPPING_URI")
    fun mkdir(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
    ): Response<Void> {
        with(artifactInfo) {
            val createRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = true,
                fullPath = getArtifactFullPath(),
                overwrite = false,
                operator = userId,
            )
            ActionAuditContext.current().setInstance(createRequest)
            nodeService.createNode(createRequest)
            return ResponseBuilder.success()
        }
    }

    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @Operation(summary = "删除节点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.DELETE)
    @DeleteMapping(DEFAULT_MAPPING_URI/* Deprecated */, "/delete/$DEFAULT_MAPPING_URI")
    fun deleteNode(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
    ): Response<NodeDeleteResult> {
        with(artifactInfo) {
            val deleteRequest = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                operator = userId,
            )
            ActionAuditContext.current().setInstance(deleteRequest)
            return ResponseBuilder.success(nodeService.deleteNode(deleteRequest))
        }
    }

    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#fullPaths",
            instanceNames = "#fullPaths"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#repoName")
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @Operation(summary = "批量删除节点")
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    @DeleteMapping("/batch/{projectId}/{repoName}")
    fun deleteNodes(
        @RequestAttribute userId: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody
        @Size(max = 200, message = "操作个数必须在0和200之间")
        fullPaths: List<String>,
    ): Response<NodeDeleteResult> {
        val nodesDeleteRequest = NodesDeleteRequest(
            projectId = projectId,
            repoName = repoName,
            fullPaths = fullPaths,
            operator = userId,
        )
        ActionAuditContext.current().setInstance(nodesDeleteRequest)
        return ResponseBuilder.success(nodeService.deleteNodes(nodesDeleteRequest))
    }

    @Operation(summary = "统计批量删除节点数")
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    @PostMapping("/batch/{projectId}/{repoName}")
    fun countBatchDeleteNode(
        @RequestAttribute userId: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody
        @Size(max = 200, message = "操作个数必须在0和200之间")
        fullPaths: List<String>,
        isFolder: Boolean = false,
    ): Response<Long> {
        val nodesDeleteRequest = NodesDeleteRequest(
            projectId = projectId,
            repoName = repoName,
            fullPaths = fullPaths,
            operator = userId,
            isFolder = isFolder,
        )
        return ResponseBuilder.success(nodeService.countDeleteNodes(nodesDeleteRequest))
    }


    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
            AuditAttribute(name = ActionAuditContent.DATE_TEMPLATE, value = "#date")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_CLEAN_CONTENT
    )
    @Operation(summary = "清理最后访问时间早于{date}的文件节点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.DELETE)
    @DeleteMapping("/clean/$DEFAULT_MAPPING_URI")
    fun deleteNodeLastModifiedBeforeDate(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        date: LocalDateTime,
    ): Response<NodeDeleteResult> {
        return ResponseBuilder.success(
            nodeService.deleteBeforeDate(
                artifactInfo.projectId,
                artifactInfo.repoName,
                date,
                userId,
                artifactInfo.getArtifactFullPath(),
            ),
        )
    }

    @Operation(summary = "创建链接节点")
    @PostMapping("/link")
    fun link(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeLinkRequest,
    ): Response<NodeDetail> {
        with(request) {
            val linkReq = NodeLinkRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                targetProjectId = targetProjectId,
                targetRepoName = targetRepoName,
                targetFullPath = targetFullPath,
                overwrite = overwrite,
                nodeMetadata = nodeMetadata,
                operator = userId,
            )
            // 校验源仓库与目标节点权限
            permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName, userId = userId)
            permissionManager.checkNodePermission(
                PermissionAction.READ, targetProjectId, targetRepoName, targetFullPath, userId = userId
            )
            return ResponseBuilder.success(nodeService.link(linkReq))
        }
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
            AuditAttribute(name = ActionAuditContent.EXPIRES_DYAS_TEMPLATE, value = "#request?.expires")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_EXPIRES_EDIT_CONTENT
    )
    @Operation(summary = "更新节点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.UPDATE)
    @PostMapping("/update/$DEFAULT_MAPPING_URI")
    fun updateNode(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody request: UserNodeUpdateRequest,
    ): Response<Void> {
        with(artifactInfo) {
            val updateRequest = NodeUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                expires = request.expires,
                operator = userId,
            )
            nodeService.updateNode(updateRequest)
            return ResponseBuilder.success()
        }
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#newFullPath")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_RENAME_CONTENT
    )
    @Operation(summary = "重命名节点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.UPDATE)
    @PostMapping("/rename/$DEFAULT_MAPPING_URI")
    fun renameNode(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestParam newFullPath: String,
    ): Response<Void> {
        with(artifactInfo) {
            permissionManager.checkNodePermission(PermissionAction.UPDATE, projectId, repoName, newFullPath)
            val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                newFullPath = newFullPath,
                operator = userId,
            )
            ActionAuditContext.current().setInstance(renameRequest)
            nodeService.renameNode(renameRequest)
            return ResponseBuilder.success()
        }
    }

    @Deprecated("/rename/{projectId}/{repoName}/**")
    @Operation(summary = "重命名节点")
    @PostMapping("/rename")
    fun renameNode(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeRenameRequest,
    ): Response<Void> {
        with(request) {
            permissionManager.checkNodePermission(PermissionAction.UPDATE, projectId, repoName, fullPath)
            permissionManager.checkNodePermission(PermissionAction.UPDATE, projectId, repoName, newFullPath)
            val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = newFullPath,
                operator = userId,
            )
            ActionAuditContext.current().setInstance(renameRequest)
            nodeService.renameNode(renameRequest)
            return ResponseBuilder.success()
        }
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#request?.srcFullPath",
            instanceNames = "#request?.srcFullPath"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#request?.srcProjectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#request?.srcRepoName"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#request?.destFullPath"),
            AuditAttribute(
                name = ActionAuditContent.NEW_PROJECT_CODE_CONTENT_TEMPLATE,
                value = "#request?.destProjectId"
            ),
            AuditAttribute(name = ActionAuditContent.NEW_REPO_NAME_CONTENT_TEMPLATE, value = "#request?.destRepoName"),
        ],
        scopeId = "#request?.srcProjectId",
        content = ActionAuditContent.NODE_MOVE_CONTENT
    )
    @Operation(summary = "移动节点")
    @PostMapping("/move")
    fun moveNode(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeMoveCopyRequest,
    ): Response<Void> {
        with(request) {
            checkCrossRepoPermission(request)
            val moveRequest = NodeMoveCopyRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destFullPath = destFullPath,
                overwrite = overwrite,
                operator = userId,
            )
            ActionAuditContext.current().setInstance(moveRequest)
            nodeService.moveNode(moveRequest)
            return ResponseBuilder.success()
        }
    }

    @AuditEntry(
        actionId = NODE_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#request?.srcFullPath",
            instanceNames = "#request?.srcFullPath"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#request?.srcProjectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#request?.srcRepoName"),
            AuditAttribute(
                name = ActionAuditContent.NEW_PROJECT_CODE_CONTENT_TEMPLATE,
                value = "#request?.destProjectId"
            ),
            AuditAttribute(name = ActionAuditContent.NEW_REPO_NAME_CONTENT_TEMPLATE, value = "#request?.destRepoName"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#request?.destFullPath")
        ],
        scopeId = "#request?.srcProjectId",
        content = ActionAuditContent.NODE_COPY_CONTENT
    )
    @Operation(summary = "复制节点")
    @PostMapping("/copy")
    fun copyNode(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeMoveCopyRequest,
    ): Response<Void> {
        with(request) {
            checkCrossRepoPermission(request)
            val copyRequest = NodeMoveCopyRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destFullPath = destFullPath,
                overwrite = overwrite,
                operator = userId,
            )
            ActionAuditContext.current().setInstance(copyRequest)
            nodeService.copyNode(copyRequest)
            return ResponseBuilder.success()
        }
    }

    @Operation(summary = "查询节点大小信息")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/size/$DEFAULT_MAPPING_URI")
    fun computeSize(artifactInfo: ArtifactInfo): Response<NodeSizeInfo> {
        val nodeSizeInfo = nodeService.computeSize(artifactInfo)
        return ResponseBuilder.success(nodeSizeInfo)
    }

    @Operation(summary = "查询时间早于{date}的节点大小信息")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/calculate/$DEFAULT_MAPPING_URI")
    fun computeSizeBefore(
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        date: LocalDateTime,
    ): Response<NodeSizeInfo> {
        val nodeSizeInfo = nodeService.computeSizeBeforeClean(artifactInfo, date)
        return ResponseBuilder.success(nodeSizeInfo)
    }

    @Operation(summary = "分页查询节点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/page/$DEFAULT_MAPPING_URI")
    fun listPageNode(
        artifactInfo: ArtifactInfo,
        nodeListOption: NodeListOption,
    ): Response<Page<NodeInfo>> {
        val nodePage = nodeService.listNodePage(artifactInfo, nodeListOption)
        return ResponseBuilder.success(nodePage)
    }

    @Operation(summary = "按sha256分页查询节点")
    @Principal(PrincipalType.ADMIN)
    @GetMapping("/page", params = ["sha256"])
    fun listPageNodeBySha256(
        @RequestParam("sha256", required = true) sha256: String,
        nodeListOption: NodeListOption,
    ): Response<Page<NodeInfo>> {
        return nodeService
            .listNodePageBySha256(sha256, nodeListOption)
            .let { ResponseBuilder.success(it) }
    }

    @Operation(summary = "查询节点删除点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/list-deleted/$DEFAULT_MAPPING_URI")
    fun listDeletedPoint(artifactInfo: ArtifactInfo): Response<List<NodeDeletedPoint>> {
        return ResponseBuilder.success(nodeService.listDeletedPoint(artifactInfo))
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
            AuditAttribute(name = ActionAuditContent.DATE_TEMPLATE, value = "#nodeRestoreOption?.deletedId")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_RESTORE_CONTENT
    )
    @Operation(summary = "恢复被删除节点")
    @Permission(type = ResourceType.NODE, action = PermissionAction.WRITE)
    @PostMapping("/restore/$DEFAULT_MAPPING_URI")
    fun restoreNode(
        artifactInfo: ArtifactInfo,
        nodeRestoreOption: NodeRestoreOption,
    ): Response<NodeRestoreResult> {
        ActionAuditContext.current().setInstance(nodeRestoreOption)
        return ResponseBuilder.success(nodeService.restoreNode(artifactInfo, nodeRestoreOption))
    }

    @Operation(summary = "自定义查询节点，如不关注总记录数请使用queryWithoutCount")
    @PostMapping("/search")
    fun search(@RequestBody queryModel: QueryModel): Response<Page<Map<String, Any?>>> {
        return ResponseBuilder.success(nodeSearchService.search(queryModel))
    }

    @Deprecated("replace with search")
    @Operation(summary = "自定义查询节点")
    @PostMapping("/query")
    fun query(@RequestBody queryModel: QueryModel): Response<Page<Map<String, Any?>>> {
        return ResponseBuilder.success(nodeSearchService.search(queryModel))
    }

    @Operation(summary = "自定义查询节点，不计算总记录数")
    @PostMapping("/queryWithoutCount")
    fun queryWithoutCount(@RequestBody queryModel: QueryModel): Response<Page<Map<String, Any?>>> {
        return ResponseBuilder.success(nodeSearchService.searchWithoutCount(queryModel))
    }

    @Operation(summary = "仓库 包数量 总览")
    @GetMapping("/search/overview")
    fun nodeGlobalSearchOverview(
        @RequestAttribute userId: String,
        @RequestParam projectId: String,
        @Parameter(name = "文件名", required = true)
        @RequestParam
        name: String,
        @Parameter(name = "仓库名 多个仓库以 `,` 分隔", required = false, example = "report,log")
        @RequestParam
        exRepo: String?,
    ): Response<List<ProjectPackageOverview>> {
        return ResponseBuilder.success(
            nodeSearchService.nodeOverview(
                userId,
                projectId,
                name,
                exRepo,
            ),
        )
    }

    @Operation(summary = "恢复归档文件")
    @PostMapping("/archive/restore")
    fun restoreArchiveNode(
        @RequestAttribute userId: String,
        @RequestBody request: UserNodeArchiveRestoreRequest,
    ): Response<List<String>> {
        with(request) {
            val restoreRequest = NodeArchiveRestoreRequest(
                projectId = projectId,
                repoName = repoName,
                path = path,
                metadata = metadata,
                operator = userId,
            )
            return ResponseBuilder.success(nodeService.restoreNode(restoreRequest))
        }
    }

    /**
     * 校验跨仓库操作权限
     */
    private fun checkCrossRepoPermission(request: UserNodeMoveCopyRequest) {
        with(request) {
            permissionManager.checkNodePermission(
                PermissionAction.WRITE,
                srcProjectId,
                srcRepoName,
                PathUtils.normalizeFullPath(srcFullPath),
            )
            val toProjectId = request.destProjectId ?: srcProjectId
            val toRepoName = request.destRepoName ?: srcRepoName
            permissionManager.checkNodePermission(
                PermissionAction.WRITE,
                toProjectId,
                toRepoName,
                PathUtils.normalizeFullPath(destFullPath),
            )
        }
    }
}
