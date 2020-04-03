package com.tencent.bkrepo.repository.pojo.project

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建项目请求")
data class UserProjectCreateRequest(
    @ApiModelProperty("项目名")
    override val name: String,
    @ApiModelProperty("显示名")
    val displayName: String,
    @ApiModelProperty("描述")
    val description: String
) : ProjectRequest
