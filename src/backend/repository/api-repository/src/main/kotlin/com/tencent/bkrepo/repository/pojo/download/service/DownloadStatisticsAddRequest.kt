package com.tencent.bkrepo.repository.pojo.download.service

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建构建下载统计次数")
data class DownloadStatisticsAddRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("构建名称", required = true)
    val artifact: String,
    @ApiModelProperty("构建版本", required = true)
    val version: String? = null
)
