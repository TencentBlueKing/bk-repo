package com.tencent.bkrepo.helm.pojo

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("helm error 包装返回模型")
data class HelmErrorResponse(
    @ApiModelProperty("错误信息")
    val error: String
)
