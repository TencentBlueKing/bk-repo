package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.AUTH_SERVICE_REPO_MODE_PREFIX
import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode
import com.tencent.bkrepo.auth.pojo.permission.RepoAuthConfigInfo
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "SERVICE_REPO_MODE", description = "服务-仓库鉴权模式接口")
@Primary
@FeignClient(AUTH_SERVICE_NAME, contextId = "ServiceRepoModeResource")
@RequestMapping(AUTH_SERVICE_REPO_MODE_PREFIX)
interface ServiceRepoModeClient {

    @Operation(summary = "查询项目下所有仓库鉴权配置（联邦同步）")
    @GetMapping("/list/{projectId}")
    fun listByProject(@PathVariable projectId: String): Response<List<RepoAuthConfigInfo>>

    @Operation(summary = "创建或更新仓库鉴权配置（联邦同步）")
    @PutMapping("/upsert")
    fun upsertRepoAuthConfig(
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam accessControlMode: AccessControlMode,
        @RequestParam(required = false) officeDenyGroupSet: Set<String>?,
        @RequestParam bkiamv3Check: Boolean
    ): Response<Boolean>
}
