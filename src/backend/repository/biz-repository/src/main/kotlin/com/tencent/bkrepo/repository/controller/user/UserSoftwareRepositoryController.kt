package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.metadata.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.service.repo.SoftwareRepositoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "软件源仓库接口")
@RestController
@RequestMapping("/api/software/repo")
class UserSoftwareRepositoryController(
    private val softwareRepositoryService: SoftwareRepositoryService
) {

    @Operation(summary = "软件源仓库列表")
    @GetMapping("/page/{pageNumber}/{pageSize}")
    fun softwareRepoPage(
        @RequestAttribute userId: String,
        @Parameter(name = "当前页", required = true, example = "0")
        @PathVariable pageNumber: Int,
        @Parameter(name = "分页大小", required = true, example = "20")
        @PathVariable pageSize: Int,
        @Parameter(name = "项目id", required = false)
        @RequestParam projectId: String?,
        @Parameter(name = "仓库名", required = false)
        @RequestParam name: String?,
        @Parameter(name = "仓库类型", required = false)
        @RequestParam type: String?
    ): Response<Page<RepositoryInfo>> {
        val repoType = type?.let { RepositoryType.valueOf(it.toUpperCase()) }
        val page = softwareRepositoryService.listRepoPage(
            projectId,
            pageNumber,
            pageSize,
            name,
            repoType
        )
        return ResponseBuilder.success(page)
    }
}
