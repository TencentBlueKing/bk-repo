package com.tencent.bkrepo.repository.pojo.project

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建项目请求")
data class ProjectRangeQueryRequest(
    @ApiModelProperty("项目Id", required = true)
    val projectIds: List<String>,
    @ApiModelProperty("分页偏移量", required = false)
    val offset: Long = 0L,
    @ApiModelProperty("分页大小", required = false)
    val limit: Int = 20
)
