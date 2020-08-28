package com.tencent.bkrepo.rpm.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("Rpm 仓库delete失败时返回信息")
data class RpmDeleteResponse(
    @ApiModelProperty("项目")
    val projectId: String,
    @ApiModelProperty("仓库")
    val repoName: String,
    @ApiModelProperty("构件uri")
    val artifactUri: String,
    @ApiModelProperty("失败原因")
    val description: String
)
