package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.service.repo.RepositoryCleanService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("仓库用户debug接口")
@RestController
@RequestMapping("/api/repo/debug")
class UserRepositoryDebugController(
    private val repositoryCleanService: RepositoryCleanService
) {
    @ApiOperation("仓库清理debug接口-调用该方法后会立即执行仓库清理动作")
    @Principal(PrincipalType.ADMIN)
    @GetMapping("/clean/{projectId}/{repoName}")
    fun cleanRepo(
        @PathVariable projectId: String,
        @PathVariable repoName: String
    ): Response<Void> {
        repositoryCleanService.cleanRepoDebug(projectId, repoName)
        return ResponseBuilder.success()
    }
}
