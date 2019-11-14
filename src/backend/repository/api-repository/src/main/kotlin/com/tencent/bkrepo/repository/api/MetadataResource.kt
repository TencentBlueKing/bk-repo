package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
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

/**
 * 节点元数据服务接口
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@Api("节点元数据服务接口")
@FeignClient(SERVICE_NAME, contextId = "MetadataResource")
@RequestMapping("/service/metadata")
interface MetadataResource {
    @ApiOperation("查询节点所有元数据")
    @GetMapping("/list/{projectId}/{repoName}")
    fun query(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "节点完整路径", required = true)
        @RequestParam fullPath: String
    ): Response<Map<String, String>>

    @ApiOperation("创建/更新元数据列表")
    @PostMapping
    fun save(@RequestBody metadataSaveRequest: MetadataSaveRequest): Response<Void>

    @ApiOperation("删除元数据")
    @DeleteMapping
    fun delete(@RequestBody metadataDeleteRequest: MetadataDeleteRequest): Response<Void>
}
