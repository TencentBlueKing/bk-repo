package com.tencent.bkrepo.helm.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.fixtool.PackageManagerResponse
import com.tencent.bkrepo.helm.service.FixToolService
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelmFixToolController(
    private val fixToolService: FixToolService
) {
    @ApiOperation("修复package管理功能")
    @GetMapping("/ext/package/populate")
    fun fixPackageVersion(): List<PackageManagerResponse> {
        return fixToolService.fixPackageVersion()
    }

    @ApiOperation("修复index.yaml文件中的制品包创建时间问题")
    @GetMapping("/ext/{projectId}/{repoName}/repairDateFormat")
    fun repairPackageCreatedDate(
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo
    ): Response<Void> {
        fixToolService.repairPackageCreatedDate(artifactInfo)
        return ResponseBuilder.success()
    }
}
