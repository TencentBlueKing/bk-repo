package com.tencent.bkrepo.replication.api

import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.constant.SERVICE_NAME
import com.tencent.bkrepo.replication.pojo.RemoteProjectInfo
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
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
@FeignClient(SERVICE_NAME, contextId = "ReplicationResource")
interface ReplicaResource {
    @GetMapping("/ping")
    fun ping(): Response<Void>

    @GetMapping("/version")
    fun version(): Response<String>

    @GetMapping("/project/list")
    fun listProject(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam projectId: String? = null,
        @RequestParam repoName: String? = null
    ): Response<List<RemoteProjectInfo>>

    @GetMapping("/node/list/{projectId}/{repoName}/{page}/{size}")
    fun listFileNode(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable page: Int = 0,
        @PathVariable size: Int = 100,
        @RequestParam path: String = "/"
    ): Response<Page<NodeInfo>>

    @GetMapping("/metadata/{projectId}/{repoName}")
    fun getMetadata(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String = "/"
    ): Response<Map<String, String>>

    @GetMapping("/permission/list")
    fun listPermission(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam projectId: String,
        @RequestParam repoName: String? = null
    ): Response<List<Permission>>

    @GetMapping("/role/list")
    fun listRole(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam projectId: String,
        @RequestParam repoName: String? = null
    ): Response<List<Role>>

    @PostMapping("/user/list")
    fun listUser(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody roleIdList: List<String>
    ): Response<List<User>>

    @GetMapping("/user/detail/{uid}")
    fun getUserDetail(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable uid: String
    ): Response<User?>

    @GetMapping("/download")
    fun downloadFile(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam fullPath: String
    ): feign.Response

    @GetMapping("/test")
    fun test(): feign.Response
}
