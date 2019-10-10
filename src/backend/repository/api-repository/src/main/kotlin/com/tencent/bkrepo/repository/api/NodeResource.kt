package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.Node
import com.tencent.bkrepo.repository.pojo.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.NodeUpdateRequest
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
import org.springframework.web.bind.annotation.RequestParam

/**
 * 资源节点服务接口
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Api("节点服务接口")
@FeignClient(SERVICE_NAME, contextId = "NodeResource")
@RequestMapping("/service/resource")
interface NodeResource {

    @ApiOperation("根据id查看节点详情")
    @GetMapping("/{id}")
    fun detail(
        @ApiParam(value = "节点id", required = true)
        @PathVariable id: String
    ): Response<Node?>

    @ApiOperation("根据路径查看节点详情")
    @GetMapping("/{repositoryId}")
    fun detail(
        @ApiParam(value = "仓库id", required = true)
        @PathVariable repositoryId: String,
        @ApiParam(value = "节点完整路径", required = true)
        @RequestParam fullPath: String
    ): Response<Node?>

    @ApiOperation("列表查询指定目录下所有节点, 只返回一层深度的节点")
    @GetMapping("/list/{repositoryId}")
    fun list(
        @ApiParam(value = "仓库id", required = true)
        @PathVariable repositoryId: String,
        @ApiParam(value = "所属目录", required = true)
        @RequestParam path: String
    ): Response<List<Node>>

    @ApiOperation("分页查询指定目录下所有节点, 只返回一层深度的节点")
    @GetMapping("/page/{page}/{size}/{repositoryId}")
    fun page(
        @ApiParam(value = "当前页", required = true, example = "0")
        @PathVariable page: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @PathVariable size: Int,
        @ApiParam(value = "仓库id", required = true)
        @PathVariable repositoryId: String,
        @ApiParam(value = "所属目录", required = true)
        @RequestParam path: String
    ): Response<Page<Node>>

    @ApiOperation("创建节点")
    @PostMapping
    fun create(
        @ApiParam(value = "创建节点请求", required = true)
        @RequestBody nodeCreateRequest: NodeCreateRequest
    ): Response<IdValue>

    @ApiOperation("修改节点")
    @PutMapping("/{id}")
    fun update(
        @ApiParam(value = "节点id", required = true)
        @PathVariable id: String,
        @ApiParam(value = "更新节点请求", required = true)
        @RequestBody nodeUpdateRequest: NodeUpdateRequest
    ): Response<Void>

    @ApiOperation("根据id删除节点")
    @DeleteMapping("/{id}")
    fun delete(
        @ApiParam(value = "节点id", required = true)
        @PathVariable id: String,
        @ApiParam(value = "修改者", required = true)
        @RequestParam modifiedBy: String
    ): Response<Void>
}
