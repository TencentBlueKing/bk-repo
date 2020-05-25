package com.tencent.bkrepo.repository.pojo.download.count.service

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("查询 日、周、月 构建下载统计次数")
class QueryWithSpecialDayRequest (
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("构建名称", required = true)
    val artifact: String,
    @ApiModelProperty("构建版本", required = false)
    val version: String?
)