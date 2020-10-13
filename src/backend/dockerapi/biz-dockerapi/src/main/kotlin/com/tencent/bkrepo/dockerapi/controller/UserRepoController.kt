package com.tencent.bkrepo.dockerapi.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.dockerapi.pojo.ImageAccount
import com.tencent.bkrepo.dockerapi.pojo.Repository
import com.tencent.bkrepo.dockerapi.service.RepoService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("仓库接口")
@RestController
@RequestMapping("/api/repo")
class UserRepoController(
    private val repoService: RepoService
) {
    @ApiOperation("创建仓库")
    @PostMapping("/create/{projectId}")
    fun createRepo(
        @PathVariable projectId: String
    ): Response<Repository> {
        return ResponseBuilder.success(repoService.createRepo(projectId))
    }

    @ApiOperation("创建账号")
    @PostMapping("/account/create/{projectId}")
    fun createAccount(
        @PathVariable projectId: String
    ): Response<ImageAccount> {
        return ResponseBuilder.success(repoService.createAccount(projectId))
    }
}
