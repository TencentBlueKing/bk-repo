package com.tencent.bkrepo.repository.pojo.download.service

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建包下载统计次数")
data class DownloadStatisticsAddRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("包唯一key", required = true)
    val packageKey: String,
    @ApiModelProperty("包名称", required = true)
    val name: String,
    @ApiModelProperty("包版本", required = true)
    val version: String
)
