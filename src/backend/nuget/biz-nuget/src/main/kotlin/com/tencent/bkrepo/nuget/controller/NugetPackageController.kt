package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.nuget.service.NugetPackageService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping("/{projectId}/{repoName}")
class NugetPackageController(
    private val nugetPackageService: NugetPackageService
)
