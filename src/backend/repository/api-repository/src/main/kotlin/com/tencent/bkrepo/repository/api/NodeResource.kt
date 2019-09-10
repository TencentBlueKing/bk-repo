package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.Resource
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

/**
 * 节点资源服务接口
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Api("节点资源服务接口")
@FeignClient(contextId = "nodeResource", value = SERVICE_NAME)
@RequestMapping("/service/resource")
interface NodeResource {

    @ApiOperation("查看资源详情")
    @GetMapping("/{id}")
    fun detail(
            @ApiParam(value = "资源id")
            @PathVariable id: String): Response<Resource>

    @ApiOperation("列表查询指定目录下所有资源, 只返回一层深度的资源")
    @GetMapping("/list/{repositoryId}")
    fun list(
            @ApiParam(value = "项目id")
            @PathVariable repositoryId: String,
            @ApiParam(value = "所属目录")
            @RequestParam path: String): Response<Page<Resource>>

    @ApiOperation("分页查询指定目录下所有资源, 只返回一层深度的资源")
    @GetMapping("/page/{page}/{size}/{repositoryId}")
    fun page(
            @ApiParam(value = "当前页")
            @PathVariable page: Long,
            @ApiParam(value = "分页大小")
            @PathVariable size: Long,
            @ApiParam(value = "项目id")
            @PathVariable repositoryId: String,
            @ApiParam(value = "所属目录")
            @RequestParam path: String): Response<Page<Resource>>

    @ApiOperation("创建资源")
    @PostMapping
    fun create(
            @ApiParam(value = "资源信息")
            @RequestBody repository: Resource): Response<Resource>

    @ApiOperation("修改资源")
    @PutMapping("/{id}")
    fun update(
            @ApiParam(value = "资源id")
            @PathVariable id: String,
            @ApiParam(value = "资源信息")
            @RequestBody repository: Resource): Response<Boolean>

    @ApiOperation("删除资源")
    @DeleteMapping("/{id}")
    fun delete(
            @ApiParam(value = "资源id")
            @PathVariable id: String): Response<Boolean>

}