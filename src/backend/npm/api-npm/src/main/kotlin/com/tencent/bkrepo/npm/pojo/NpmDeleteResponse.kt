package com.tencent.bkrepo.npm.pojo

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("npm delete返回包装模型")
data class NpmDeleteResponse(
    @ApiModelProperty("返回状态")
    val ok: Boolean,
    @ApiModelProperty("包名称")
    val id: String,
    @ApiModelProperty("rev")
    val rev: String
)
