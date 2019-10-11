package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("项目")
data class Project(
    @ApiModelProperty("ID")
    val id: String,
    @ApiModelProperty("名称")
    val name: String,
    @ApiModelProperty("显示名称")
    val displayName: String,
    @ApiModelProperty("描述")
    val description: String
)
