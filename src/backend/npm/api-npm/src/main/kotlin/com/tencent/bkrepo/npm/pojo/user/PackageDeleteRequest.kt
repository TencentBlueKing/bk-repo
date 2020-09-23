package com.tencent.bkrepo.npm.pojo.user

import io.swagger.annotations.ApiModelProperty
import io.swagger.annotations.ApiOperation

@ApiOperation("删除包请求")
data class PackageDeleteRequest (
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    val fullPath: String,
    @ApiModelProperty("操作用户", required = true)
    val operator: String
)