package com.tencent.bkrepo.npm.pojo.migration

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("迁移错误数据信息")
data class MigrationErrorDataInfo(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("迁移次数计数器")
    val counter: Int,
    @ApiModelProperty("错误数据")
    val errorData: Set<*>,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String
)
