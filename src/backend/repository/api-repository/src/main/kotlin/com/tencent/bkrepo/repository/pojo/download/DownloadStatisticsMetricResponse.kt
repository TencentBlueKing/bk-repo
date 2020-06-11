package com.tencent.bkrepo.repository.pojo.download

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("日、周、月 返回下载统计量")
data class DownloadStatisticsMetricResponse(
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("构建名称")
    val artifact: String,
    @ApiModelProperty("构建版本")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val version: String?,
    @ApiModelProperty("时间段下载量")
    val statisticsMetrics: List<DownloadStatisticsMetric>
)
