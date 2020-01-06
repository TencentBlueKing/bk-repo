package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("node查询结果")
data class NodeResult(
    @ApiModelProperty("target")
    val target: String,
    @ApiModelProperty("datapoints")
    val datapoints: List<List<Long>>
)
