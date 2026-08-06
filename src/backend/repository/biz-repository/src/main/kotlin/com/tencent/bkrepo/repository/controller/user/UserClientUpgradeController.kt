package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientUpgradeCheckResponse
import com.tencent.bkrepo.repository.service.clientupgrade.ClientVersionConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "客户端升级检查")
@RestController
@RequestMapping("/api/client/upgrade")
class UserClientUpgradeController(
    private val clientVersionConfigService: ClientVersionConfigService,
) {

    @Principal(PrincipalType.GENERAL)
    @Operation(summary = "查询升级信息")
    @GetMapping("/check")
    fun check(
        @RequestAttribute userId: String,
        @RequestParam currentVersion: String,
        @RequestParam productId: String,
        @RequestParam platform: String,
        @RequestParam arch: String,
    ): Response<ClientUpgradeCheckResponse> {
        return ResponseBuilder.success(
            clientVersionConfigService.checkUpgrade(
                forUserId = userId,
                currentVersion = currentVersion,
                productId = productId,
                platform = platform,
                arch = arch,
            ),
        )
    }
}
