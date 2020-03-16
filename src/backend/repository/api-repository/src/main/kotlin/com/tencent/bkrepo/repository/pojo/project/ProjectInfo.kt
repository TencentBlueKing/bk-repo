package com.tencent.bkrepo.repository.pojo.project

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("项目信息")
data class ProjectInfo(
    @ApiModelProperty("名称")
    val name: String,
    @ApiModelProperty("显示名称")
    val displayName: String,
    @ApiModelProperty("描述")
    val description: String,
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建日期")
    val createdDate: String,
    @ApiModelProperty("上次修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("上次修改日期")
    val lastModifiedDate: String
)
