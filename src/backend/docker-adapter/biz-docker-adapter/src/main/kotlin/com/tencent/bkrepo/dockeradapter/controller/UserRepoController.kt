package com.tencent.bkrepo.dockeradapter.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.dockeradapter.pojo.ImageAccount
import com.tencent.bkrepo.dockeradapter.pojo.Repository
import com.tencent.bkrepo.dockeradapter.service.RepoService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
    @PostMapping("/account/create")
    fun createAccount(
        @RequestParam projectId: String
    ): Response<ImageAccount> {
        return ResponseBuilder.success(repoService.createAccount(projectId))
    }
}
