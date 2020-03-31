package com.tencent.bkrepo.repository.pojo.project

import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建项目请求")
data class ProjectCreateRequest(
    @ApiModelProperty("项目名", required = true)
    val name: String,
    @ApiModelProperty("显示名", required = true)
    val displayName: String,
    @ApiModelProperty("描述", required = true)
    val description: String? = null,

    @ApiModelProperty("操作用户", required = false)
    val operator: String = SYSTEM_USER
)
