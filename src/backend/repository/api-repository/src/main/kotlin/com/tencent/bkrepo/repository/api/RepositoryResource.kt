package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.Repository
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 仓库服务接口
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
@Api("仓库服务接口")
@FeignClient(contextId = "repositoryResource", value = SERVICE_NAME)
@RequestMapping("/service/repository")
interface RepositoryResource {

    @ApiOperation("查看仓库详情")
    @GetMapping("/{id}")
    fun detail(
        @ApiParam(value = "仓库id")
        @PathVariable id: String
    ): Response<Repository>

    @ApiOperation("列表查询项目所有仓库")
    @GetMapping("/list/{projectId}")
    fun list(
        @ApiParam(value = "项目id")
        @PathVariable projectId: String
    ): Response<List<Repository>>

    @ApiOperation("分页查询项目所有仓库")
    @GetMapping("/page/{page}/{size}/{projectId}")
    fun page(
        @ApiParam(value = "当前页")
        @PathVariable page: Long,
        @ApiParam(value = "分页大小")
        @PathVariable size: Long,
        @ApiParam(value = "项目id")
        @PathVariable projectId: String
    ): Response<Page<Repository>>

    @ApiOperation("创建仓库")
    @PostMapping
    fun create(
        @ApiParam(value = "仓库信息")
        @RequestBody repository: Repository
    ): Response<Repository>

    @ApiOperation("修改仓库")
    @PutMapping("/{id}")
    fun update(
        @ApiParam(value = "仓库id")
        @PathVariable id: String,
        @ApiParam(value = "仓库信息")
        @RequestBody repository: Repository
    ): Response<Boolean>

    @ApiOperation("删除仓库")
    @DeleteMapping("/{id}")
    fun delete(
        @ApiParam(value = "仓库id")
        @PathVariable id: String
    ): Response<Boolean>
}
