package com.tencent.bkrepo.metadata.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.metadata.constant.SERVICE_NAME
import com.tencent.bkrepo.metadata.pojo.Metadata
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
 * 元数据服务接口
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Api("节点资源服务接口")
@FeignClient(SERVICE_NAME, contextId = "MetadataResource")
@RequestMapping("/service/metadata")
interface MetadataResource {

    @ApiOperation("查看元数据详情")
    @GetMapping("/{id}")
    fun detail(
        @ApiParam(value = "元数据id")
        @PathVariable id: String
    ): Response<Metadata>

    @ApiOperation("列表查询资源所有元数据")
    @GetMapping("/list/{resourceId}")
    fun list(
        @ApiParam(value = "资源id")
        @PathVariable resourceId: String
    ): Response<List<Metadata>>

    @ApiOperation("创建元数据")
    @PostMapping
    fun create(
        @ApiParam(value = "元数据信息")
        @RequestBody repository: Metadata
    ): Response<Metadata>

    @ApiOperation("创建元数据列表")
    @PostMapping("/list")
    fun create(
        @ApiParam(value = "元数据信息")
        @RequestBody repository: List<Metadata>
    ): Response<List<Metadata>>

    @ApiOperation("修改元数据")
    @PutMapping("/{id}")
    fun update(
        @ApiParam(value = "元数据id")
        @PathVariable id: String,
        @ApiParam(value = "元数据信息")
        @RequestBody repository: Metadata
    ): Response<Boolean>

    @ApiOperation("删除元数据")
    @DeleteMapping("/{id}")
    fun delete(
        @ApiParam(value = "元数据id")
        @PathVariable id: String
    ): Response<Boolean>
}
