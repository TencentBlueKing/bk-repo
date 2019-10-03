package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("元数据信息")
data class PermissionData(
    @ApiModelProperty("项目ID")
    val projectId: String?,
    @ApiModelProperty("仓库ID")
    val repoId: String?
)
