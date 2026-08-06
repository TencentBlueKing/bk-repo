package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.job.FederationAutoEnableBatchJob
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "联邦自动开启接口")
@RestController
@Principal(type = PrincipalType.ADMIN)
@RequestMapping("/api/federation/auto-enable")
class UserFederationAutoEnableController(
    private val federationAutoEnableBatchJob: FederationAutoEnableBatchJob,
) {

    @Operation(summary = "手动触发存量仓库联邦自动开启", description = "与定时任务等效，幂等，已有联邦配置的仓库会被跳过")
    @PostMapping
    fun execute(): Response<Void> {
        federationAutoEnableBatchJob.execute()
        return ResponseBuilder.success()
    }
}
