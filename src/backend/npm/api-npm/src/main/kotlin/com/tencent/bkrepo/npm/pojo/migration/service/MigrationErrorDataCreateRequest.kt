package com.tencent.bkrepo.npm.pojo.migration.service

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建错误数据请求")
data class MigrationErrorDataCreateRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("计数器", required = true)
    val counter: Int,
    @ApiModelProperty("错误数据集合", required = true)
    val errorData: String,
    @ApiModelProperty("操作用户", required = false)
    val operator: String = "system"
)
