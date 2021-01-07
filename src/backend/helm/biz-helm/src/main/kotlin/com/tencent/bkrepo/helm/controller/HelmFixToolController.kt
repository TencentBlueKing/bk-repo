package com.tencent.bkrepo.helm.controller

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
    @GetMapping("/fixPackageVersion")
    fun fixPackageVersion(): List<PackageManagerResponse>{
        return fixToolService.fixPackageVersion()
    }
}