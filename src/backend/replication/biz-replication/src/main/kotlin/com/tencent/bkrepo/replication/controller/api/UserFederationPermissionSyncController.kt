package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.job.FederationPermissionSyncJob
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "联邦权限同步接口")
@RestController
@Principal(type = PrincipalType.ADMIN)
@RequestMapping("/api/federation/permission-sync")
class UserFederationPermissionSyncController(
    private val federationPermissionSyncJob: FederationPermissionSyncJob,
) {

    @Operation(summary = "手动触发联邦权限全量同步", description = "与定时任务等效，持有分布式锁时自动跳过")
    @PostMapping
    fun sync(): Response<Void> {
        federationPermissionSyncJob.sync()
        return ResponseBuilder.success()
    }
}
