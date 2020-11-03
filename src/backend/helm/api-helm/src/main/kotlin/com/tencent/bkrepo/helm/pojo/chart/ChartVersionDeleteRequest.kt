package com.tencent.bkrepo.helm.pojo.chart

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("chart删除请求")
data class ChartVersionDeleteRequest(
    @ApiModelProperty("所属项目id", required = true)
    val projectId: String,
    @ApiModelProperty("所属仓库id", required = true)
    val repoName: String,
    @ApiModelProperty("chart名称", required = true)
    val name: String,
    @ApiModelProperty("chart版本", required = true)
    val version: String,
    @ApiModelProperty("操作用户id", required = true)
    val operator: String
)