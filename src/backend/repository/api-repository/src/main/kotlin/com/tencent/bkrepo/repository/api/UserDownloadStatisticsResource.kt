package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Api("用户构建下载量统计服务接口")
@RequestMapping("/api/download/statistics")
interface UserDownloadStatisticsResource {
    @ApiOperation("查询构建下载量")
    @GetMapping("/query$DEFAULT_MAPPING_URI")
    fun query(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("构建名称", required = true)
        artifact: String,
        @ApiParam("构建版本", required = false)
        version: String? = null,
        @ApiParam("开始日期", required = true)
        @RequestParam startDate: LocalDate,
        @ApiParam("结束日期", required = true)
        @RequestParam endDate: LocalDate
    ): Response<DownloadStatisticsResponse>

    @ApiOperation("查询构建在 日、周、月 的下载量")
    @GetMapping("/query/special/$DEFAULT_MAPPING_URI")
    fun queryForSpecial(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("构建名称", required = true)
        artifact: String,
        @ApiParam("构建版本", required = false)
        version: String? = null
    ): Response<DownloadStatisticsMetricResponse>
}
