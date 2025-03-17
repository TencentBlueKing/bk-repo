package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.service.packages.PackageRepairService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class UserPackageRepairController(
    private val packageRepairService: PackageRepairService
) {

    @Operation(summary = "修改历史版本")
    @Principal(PrincipalType.ADMIN)
    @GetMapping("/version/history/repair")
    fun repairHistoryVersion(): Response<Void> {
        packageRepairService.repairHistoryVersion()
        return ResponseBuilder.success()
    }

    @Operation(summary = "修正包的版本数")
    @Principal(PrincipalType.ADMIN)
    @PutMapping("/package/version/recount")
    fun repairVersionCount(): Response<Void> {
        packageRepairService.repairVersionCount()
        return ResponseBuilder.success()
    }
}
