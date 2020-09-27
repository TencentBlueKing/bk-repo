package com.tencent.bkrepo.repository.pojo.download

import io.swagger.annotations.ApiModelProperty

data class DownloadStatisticsMetric(
    @ApiModelProperty("时间段")
    val description: String,
    @ApiModelProperty("下载量")
    val count: Long
)
