package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 仓库服务接口
 */
@Api("仓库服务接口")
@Primary
@FeignClient(SERVICE_NAME, contextId = "RepositoryClient")
@RequestMapping("/service/repo")
interface RepositoryClient {

    @Deprecated("replace with getRepoDetail")
    @GetMapping("/query/{projectId}/{repoName}/{type}")
    fun query(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "仓库类型", required = true)
        @PathVariable type: String
    ): Response<RepositoryDetail?>


    @Deprecated("replace with getRepoDetail")
    @GetMapping("/query/{projectId}/{repoName}")
    fun query(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String
    ): Response<RepositoryDetail?>


    @ApiOperation("查询仓库信息")
    @GetMapping("/info/{projectId}/{repoName}")
    fun getRepoInfo(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String
    ): Response<RepositoryInfo?>

    @ApiOperation("查询仓库详情")
    @GetMapping("/detail/{projectId}/{repoName}")
    fun getRepoDetail(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String
    ): Response<RepositoryDetail?>

    @ApiOperation("查询仓库详情")
    @GetMapping("/detail/{projectId}/{repoName}/{type}")
    fun getRepoDetail(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "仓库类型", required = true)
        @PathVariable type: String? = null
    ): Response<RepositoryDetail?>

    @ApiOperation("列表查询项目所有仓库")
    @GetMapping("/list/{projectId}")
    fun list(
        @ApiParam(value = "项目id", required = true)
        @PathVariable projectId: String
    ): Response<List<RepositoryInfo>>

    @ApiOperation("分页查询项目所有仓库")
    @GetMapping("/page/{pageNumber}/{pageSize}/{projectId}")
    fun page(
        @ApiParam(value = "当前页", required = true, example = "1")
        @PathVariable pageNumber: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @PathVariable pageSize: Int,
        @ApiParam(value = "项目id", required = true)
        @PathVariable projectId: String
    ): Response<Page<RepositoryInfo>>

    @ApiOperation("创建仓库")
    @PostMapping
    fun create(
        @RequestBody repoCreateRequest: RepoCreateRequest
    ): Response<RepositoryDetail>

    @ApiOperation("修改仓库")
    @PutMapping
    fun update(
        @RequestBody repoUpdateRequest: RepoUpdateRequest
    ): Response<Void>

    @ApiOperation("删除仓库")
    @DeleteMapping
    fun delete(
        @RequestBody repoDeleteRequest: RepoDeleteRequest
    ): Response<Void>
}
