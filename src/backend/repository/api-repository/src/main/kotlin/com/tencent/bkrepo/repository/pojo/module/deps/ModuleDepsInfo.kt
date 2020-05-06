package com.tencent.bkrepo.repository.pojo.module.deps

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("节点依赖信息")
data class ModuleDepsInfo(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("资源名称")
    val name: String,
    @ApiModelProperty("被依赖的资源名称")
    val deps: String,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String
)
