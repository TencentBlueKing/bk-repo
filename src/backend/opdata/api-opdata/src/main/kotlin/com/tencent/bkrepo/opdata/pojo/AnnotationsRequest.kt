package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("annotation查询")
data class AnnotationsRequest(
    @ApiModelProperty("appId")
    val appId: String,
    @ApiModelProperty("locked状态")
    val locked: Boolean
)
