package com.tencent.bkrepo.replication.api

import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.constant.SERVICE_NAME
import com.tencent.bkrepo.replication.pojo.request.NodeExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RequestMapping("/replica")
@FeignClient(SERVICE_NAME, contextId = "ReplicationClient")
interface ReplicationClient {

    @GetMapping("/ping")
    fun ping(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String): Response<Void>

    @GetMapping("/version")
    fun version(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String): Response<String>

    @PostMapping("/node/exist/list")
    fun checkNodeExistList(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeExistCheckRequest: NodeExistCheckRequest
    ): Response<List<String>>

    @GetMapping("/node/exist")
    fun checkNodeExist(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam fullPath: String
    ): Response<Boolean>

    @PostMapping("/user")
    fun replicaUser(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody userReplicaRequest: UserReplicaRequest
    ): Response<User>

    @PostMapping("/role")
    fun replicaRole(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody roleReplicaRequest: RoleReplicaRequest
    ): Response<Role>

    @PostMapping("/permission")
    fun replicaPermission(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody permissionCreateRequest: CreatePermissionRequest
    ): Response<Void>

    @PostMapping("/role/user/{rid}")
    fun replicaUserRoleRelationShip(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable rid: String,
        @RequestBody userIdList: List<String>
    ): Response<Void>

    @GetMapping("/permission/list")
    fun listPermission(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam resourceType: ResourceType,
        @RequestParam projectId: String,
        @RequestParam repoName: String? = null
    ): Response<List<Permission>>

    @PostMapping("/node/create")
    fun replicaNodeCreateRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeCreateRequest: NodeCreateRequest
    ): Response<NodeDetail>

    @PostMapping("/node/rename")
    fun replicaNodeRenameRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeRenameRequest: NodeRenameRequest
    ): Response<Void>

    @PostMapping("/node/update")
    fun replicaNodeUpdateRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeUpdateRequest: NodeUpdateRequest
    ): Response<Void>

    @PostMapping("/node/copy")
    fun replicaNodeCopyRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeCopyRequest: NodeCopyRequest
    ): Response<Void>

    @PostMapping("/node/move")
    fun replicaNodeMoveRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeMoveRequest: NodeMoveRequest
    ): Response<Void>

    @PostMapping("/node/delete")
    fun replicaNodeDeleteRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody nodeDeleteRequest: NodeDeleteRequest
    ): Response<Void>

    @PostMapping("/repo/create")
    fun replicaRepoCreateRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody request: RepoCreateRequest
    ): Response<RepositoryDetail>

    @PostMapping("/repo/update")
    fun replicaRepoUpdateRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody request: RepoUpdateRequest
    ): Response<Void>

    @PostMapping("/repo/delete")
    fun replicaRepoDeleteRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody request: RepoDeleteRequest
    ): Response<Void>

    @PostMapping("/project/create")
    fun replicaProjectCreateRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody request: ProjectCreateRequest
    ): Response<ProjectInfo>

    @PostMapping("/metadata/save")
    fun replicaMetadataSaveRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody request: MetadataSaveRequest
    ): Response<Void>

    @PostMapping("/metadata/delete")
    fun replicaMetadataDeleteRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody request: MetadataDeleteRequest
    ): Response<Void>
}
