package com.tencent.bkrepo.replication.api

import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.constant.SERVICE_NAME
import com.tencent.bkrepo.replication.pojo.request.ProjectReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RepoReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
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
@FeignClient(SERVICE_NAME, contextId = "ReplicationService")
interface ReplicationClient {

    @GetMapping("/ping")
    fun ping(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String): Response<Void>

    @GetMapping("/version")
    fun version(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String): Response<String>

    @GetMapping("/node/exist")
    fun checkNodeExist(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam fullPath: String
    ): Response<Boolean>

    @PostMapping("/project")
    fun replicaProject(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody replicaRequest: ProjectReplicaRequest
    ): Response<ProjectInfo>

    @PostMapping("/repo")
    fun replicaRepository(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody repoReplicaRequest: RepoReplicaRequest
    ): Response<RepositoryInfo>

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
}
