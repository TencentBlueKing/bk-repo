package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.download.count.CountResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.CountWithSpecialDayInfoResponse
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Api("节点服务接口")
@FeignClient(SERVICE_NAME, contextId = "ArtifactDownloadCountResource")
@RequestMapping("/service/download/count")
interface ArtifactDownloadCountResource {
    @ApiOperation("创建构建下载量")
    @PostMapping("/create")
    fun create(@RequestBody countCreateRequest: DownloadCountCreateRequest): Response<Void>

    @ApiOperation("查询构建下载量")
    @GetMapping("/find/{projectId}/{repoName}/{artifact}")
    fun find(
        @ApiParam("所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam("构建名称", required = true)
        @PathVariable artifact: String,
        @ApiParam("构建版本", required = false)
        @RequestParam version: String? = null,
        @ApiParam("开始日期", required = true)
        @RequestParam startDay: LocalDate,
        @ApiParam("结束日期", required = true)
        @RequestParam endDay: LocalDate
    ): Response<CountResponseInfo>

    @ApiOperation("查询构建在 日、周、月 的下载量")
    @GetMapping("/query/{projectId}/{repoName}/{artifact}")
    fun query(
        @ApiParam("所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam("构建名称", required = true)
        @PathVariable artifact: String,
        @ApiParam("构建版本", required = false)
        @RequestParam version: String? = null
    ): Response<CountWithSpecialDayInfoResponse>
}