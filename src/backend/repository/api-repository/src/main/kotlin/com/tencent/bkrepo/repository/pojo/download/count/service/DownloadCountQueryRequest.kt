package com.tencent.bkrepo.repository.pojo.download.count.service

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDate

@ApiModel("查询时间段内构建下载统计次数")
data class DownloadCountQueryRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("构建名称", required = true)
    val artifact: String,
    @ApiModelProperty("构建版本", required = false)
    val version: String?,
    @ApiModelProperty("开始时间", required = true)
    val startTime: LocalDate,
    @ApiModelProperty("结束时间", required = true)
    val endTime: LocalDate
)