package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.pojo.router.AddRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.AddRouterPolicyRequest
import com.tencent.bkrepo.common.metadata.pojo.router.NodeLocation
import com.tencent.bkrepo.common.metadata.pojo.router.RemoveRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.RemoveRouterPolicyRequest
import com.tencent.bkrepo.common.metadata.pojo.router.RouterNode
import com.tencent.bkrepo.common.metadata.pojo.router.RouterPolicy
import com.tencent.bkrepo.common.metadata.pojo.router.user.UserAddRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.user.UserAddRouterPolicyRequest
import com.tencent.bkrepo.common.metadata.pojo.router.user.UserRemoveRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.user.UserRemoveRouterPolicyRequest
import com.tencent.bkrepo.common.metadata.service.router.RouterAdminService
import com.tencent.bkrepo.common.metadata.service.router.RouterControllerService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/router/admin")
class RouterAdminController(
    val routerAdminService: RouterAdminService,
    val routerControllerService: RouterControllerService,
) {

    @Operation(summary = "增加路由策略")
    @PostMapping("/policy")
    fun addPolicy(
        @RequestAttribute userId: String,
        @RequestBody request: UserAddRouterPolicyRequest,
    ): Response<RouterPolicy> {
        with(request) {
            val addRouterPolicyRequest = AddRouterPolicyRequest(
                users = users,
                projectIds = projectIds,
                destRouterNodeId = destRouterNodeId,
                operator = userId,
            )
            return ResponseBuilder.success(routerAdminService.addPolicy(addRouterPolicyRequest))
        }
    }

    @Operation(summary = "删除路由策略")
    @DeleteMapping("/policy")
    fun removePolicy(
        @RequestAttribute userId: String,
        @RequestBody request: UserRemoveRouterPolicyRequest,
    ): Response<Void> {
        with(request) {
            val removeRouterPolicyRequest = RemoveRouterPolicyRequest(
                policyId = policyId,
                operator = userId,
            )
            routerAdminService.removePolicy(removeRouterPolicyRequest)
            return ResponseBuilder.success()
        }
    }

    @Operation(summary = "新增路由节点")
    @PostMapping("/node")
    fun addRouterNode(
        @RequestAttribute userId: String,
        @RequestBody request: UserAddRouterNodeRequest,
    ): Response<RouterNode> {
        with(request) {
            val addRouterNodeRequest = AddRouterNodeRequest(
                id = id,
                name = name,
                description = description,
                type = type,
                location = location,
                operator = userId,
            )
            return ResponseBuilder.success(routerAdminService.addRouterNode(addRouterNodeRequest))
        }
    }

    @Operation(summary = "移除路由节点")
    @DeleteMapping("/node")
    fun removeRouterNode(
        @RequestAttribute userId: String,
        @RequestBody request: UserRemoveRouterNodeRequest,
    ): Response<Void> {
        with(request) {
            val removeRouterNodeRequest = RemoveRouterNodeRequest(
                nodeId = nodeId,
                operator = userId,
            )
            routerAdminService.removeRouterNode(removeRouterNodeRequest)
            return ResponseBuilder.success()
        }
    }

    @Operation(summary = "获取所有路由策略")
    @GetMapping("/policy/list")
    fun listPolicies(): Response<List<RouterPolicy>> {
        return ResponseBuilder.success(routerAdminService.listPolicies())
    }

    @Operation(summary = "获取所有路由节点")
    @GetMapping("/node/list")
    fun listRouterNodes(): Response<List<RouterNode>> {
        return ResponseBuilder.success(routerAdminService.listRouterNodes())
    }

    @Operation(summary = "获取文件节点位置")
    @GetMapping("/node/location")
    fun listNodeLocations(
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam fullPath: String,
    ): Response<List<NodeLocation>> {
        return ResponseBuilder.success(
            routerAdminService.listNodeLocations(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
            ),
        )
    }

    /**
     * 新增文件节点
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 文件完整路径
     * @param routerNodeId 所在节点id
     * */
    @Operation(summary = "新增文件节点位置")
    @PostMapping("/node/location")
    fun addNode(projectId: String, repoName: String, fullPath: String, routerNodeId: String): Response<NodeLocation> {
        return ResponseBuilder.success(
            routerControllerService.addNode(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                routerNodeId = routerNodeId,
            ),
        )
    }

    /**
     * 移除文件节点
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 文件完整路径
     * @param routerNodeId 所在节点id
     * */
    @Operation(summary = "删除文件节点位置")
    @DeleteMapping("/node/location")
    fun removeNode(projectId: String, repoName: String, fullPath: String, routerNodeId: String): Response<Void> {
        routerControllerService.removeNode(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            routerNodeId = routerNodeId,
        )
        return ResponseBuilder.success()
    }
}