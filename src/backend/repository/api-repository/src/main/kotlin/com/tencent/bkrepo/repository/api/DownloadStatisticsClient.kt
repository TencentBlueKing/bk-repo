package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Api("构建下载量统计服务接口")
@Primary
@FeignClient(SERVICE_NAME, contextId = "DownloadStatisticsClient")
@RequestMapping("/service/download/statistics")
interface DownloadStatisticsClient {

    @ApiOperation("创建构建下载量")
    @PostMapping("/add")
    fun add(@RequestBody statisticsAddRequest: DownloadStatisticsAddRequest): Response<Void>

    @ApiOperation("查询构建下载量")
    @GetMapping("/query/{projectId}/{repoName}")
    fun query(
        @ApiParam("所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam("构建名称", required = true)
        @RequestParam artifact: String,
        @ApiParam("构建版本", required = false)
        @RequestParam version: String? = null,
        @ApiParam("开始日期", required = false)
        @RequestParam startDay: LocalDate? = null,
        @ApiParam("结束日期", required = false)
        @RequestParam endDay: LocalDate? = null
    ): Response<DownloadStatisticsResponse>

    @ApiOperation("查询构建在 日、周、月 的下载量")
    @GetMapping("/query/special/{projectId}/{repoName}/")
    fun queryForSpecial(
        @ApiParam("所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam("构建名称", required = true)
        @RequestParam artifact: String,
        @ApiParam("构建版本", required = false)
        @RequestParam version: String? = null
    ): Response<DownloadStatisticsMetricResponse>
}
