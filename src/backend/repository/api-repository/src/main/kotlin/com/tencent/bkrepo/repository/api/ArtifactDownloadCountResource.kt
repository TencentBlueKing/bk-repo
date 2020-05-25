package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.download.count.CountResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountQueryRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Api("节点服务接口")
@FeignClient(SERVICE_NAME, contextId = "ArtifactDownloadCountResource")
@RequestMapping("/service/artifact/download/count")
interface ArtifactDownloadCountResource {
    @ApiOperation("创建构建下载量")
    @PostMapping("/create")
    fun create(@RequestBody countCreateRequest: DownloadCountCreateRequest): Response<Void>

    @ApiOperation("查询构建下载量")
    @GetMapping("/find")
    fun find(@RequestBody countQueryRequest: DownloadCountQueryRequest): Response<CountResponseInfo>
}