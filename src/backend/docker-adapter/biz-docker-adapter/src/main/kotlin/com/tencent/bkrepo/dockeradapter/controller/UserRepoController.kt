package com.tencent.bkrepo.dockeradapter.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.dockeradapter.pojo.Account
import com.tencent.bkrepo.dockeradapter.pojo.Repository
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("仓库接口")
@RestController
@RequestMapping("/api/repo")
class UserRepoController(

) {
    @ApiOperation("创建仓库")
    @PostMapping("/create")
    private fun createRepo(
        @RequestParam projectId: String
    ): Response<Repository> {
        return ResponseBuilder.success(Repository("dummy", "dummy", "dummy"))
    }

    @ApiOperation("创建账号")
    @PostMapping("/account/create")
    fun createAccount(
        @RequestParam projectId: String
    ): Response<Account> {
        return ResponseBuilder.success(Account("dummy", "dummy"))
    }
}
