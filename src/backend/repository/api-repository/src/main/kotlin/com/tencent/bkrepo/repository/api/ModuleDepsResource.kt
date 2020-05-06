package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.module.deps.ModuleDepsInfo
import com.tencent.bkrepo.repository.pojo.module.deps.service.DepsCreateRequest
import com.tencent.bkrepo.repository.pojo.module.deps.service.DepsDeleteRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("包依赖信息接口")
@FeignClient(SERVICE_NAME, contextId = "ModuleDepsResource")
@RequestMapping("/service/module/deps")
interface ModuleDepsResource {
    @ApiOperation("创建资源依赖关系")
    @PostMapping("/create")
    fun create(
        @RequestBody depsCreateRequest: DepsCreateRequest
    ): Response<ModuleDepsInfo>

    @ApiOperation("批量创建资源依赖关系")
    @PostMapping("/batch/create")
    fun batchCreate(
        @RequestBody depsCreateRequest: List<DepsCreateRequest>
    ): Response<Void>

    @ApiOperation("删除单个资源的单个依赖关系")
    @DeleteMapping("/delete")
    fun delete(
        @RequestBody depsDeleteRequest: DepsDeleteRequest
    ): Response<Void>

    @ApiOperation("删除单个资源的所有资源依赖关系")
    @DeleteMapping("/delete/all")
    fun deleteAllByName(
        @RequestBody depsDeleteRequest: DepsDeleteRequest
    ): Response<Void>

    @ApiOperation("查询某个资源节点被依赖的单个资源名称")
    @GetMapping("/find/{projectId}/{repoName}")
    fun find(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "资源名称", required = true)
        @RequestParam name: String,
        @ApiParam(value = "被依赖资源名称", required = true)
        @RequestParam deps: String
    ): Response<ModuleDepsInfo>

    @ApiOperation("列表查询某个资源节点被依赖的所有资源名称")
    @GetMapping("/list/{projectId}/{repoName}")
    fun list(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "资源名称", required = true)
        @RequestParam name: String
    ): Response<List<ModuleDepsInfo>>

    @ApiOperation("分页查询某个资源节点被依赖的所有资源名称")
    @GetMapping("/page/{projectId}/{repoName}/{page}/{size}")
    fun page(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "当前页", required = true, example = "0")
        @PathVariable page: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @PathVariable size: Int,
        @ApiParam(value = "资源名称", required = true)
        @RequestParam name: String
    ): Response<Page<ModuleDepsInfo>>
}
