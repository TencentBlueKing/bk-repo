package com.tencent.bkrepo.replication.pojo.request

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("制品包信息")
data class PackageInfo(
    @ApiModelProperty("包名称")
    val name: String,
    @ApiModelProperty("包唯一key")
    val packageKey: String,
    @ApiModelProperty("包版本")
    // val version: List<String>
    val version: String
)
