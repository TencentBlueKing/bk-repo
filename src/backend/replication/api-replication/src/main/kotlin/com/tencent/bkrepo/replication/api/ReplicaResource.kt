package com.tencent.bkrepo.replication.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.constant.SERVICE_NAME
import com.tencent.bkrepo.replication.pojo.RemoteProjectInfo
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RequestMapping("/")
@FeignClient(SERVICE_NAME, contextId = "ReplicationResource")
interface ReplicaResource {

    @GetMapping("/ping")
    fun ping(): Response<Void>

    @PostMapping("/version")
    fun version(): Response<Any>

    @GetMapping("/project/list")
    fun listProject(
        @RequestParam projectId: String? = null,
        @RequestParam repoName: String? = null
    ): Response<List<RemoteProjectInfo>>

    @GetMapping("/node/list/{projectId}/{repoName}/{page}/{size}")
    fun listFileNode(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable page: Int = 0,
        @PathVariable size: Int = 100,
        @RequestParam path: String = "/"
    ): Response<Page<NodeInfo>>

    @GetMapping("/node/metadata/{projectId}/{repoName}")
    fun getMetadata(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String = "/"
    ): Response<Map<String, String>>

    @GetMapping("/download")
    fun downloadFile(
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam fullPath: String
    ): feign.Response
}
