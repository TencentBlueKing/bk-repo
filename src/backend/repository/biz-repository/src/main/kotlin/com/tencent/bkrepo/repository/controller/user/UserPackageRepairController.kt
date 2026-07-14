package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.service.packages.PackageRepairService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.packages.PackageMetadataRepairResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
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

    @Operation(summary = "按范围修复 Package 元数据（latest、historyVersion）")
    @Principal(PrincipalType.ADMIN)
    @PostMapping("/package/metadata/repair")
    fun repairPackageMetadata(
        @Parameter(description = "项目 ID", required = true)
        @RequestParam projectId: String,
        @Parameter(description = "仓库名", required = true)
        @RequestParam repoName: String,
        @Parameter(description = "包唯一标识，可选；不传则修复该仓库下全部 package", required = false)
        @RequestParam(required = false) packageKey: String?
    ): Response<PackageMetadataRepairResult> {
        val result = packageRepairService.repairPackageMetadata(projectId, repoName, packageKey)
        return ResponseBuilder.success(result)
    }
}
