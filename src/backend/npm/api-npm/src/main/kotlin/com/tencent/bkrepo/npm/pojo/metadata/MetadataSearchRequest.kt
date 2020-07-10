package com.tencent.bkrepo.npm.pojo.metadata

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("npm search 查询条件")
data class MetadataSearchRequest(
    @ApiModelProperty("查询内容", required = true)
    val text: String,
    @ApiModelProperty("查询包的个数", required = false)
    val size: Int = 10,
    @ApiModelProperty("起始页", required = false)
    val from: Int = 0,
    @ApiModelProperty("质量")
    val quality: Double = 0.0,
    @ApiModelProperty("受欢迎程度")
    val popularity: Double = 0.0,
    @ApiModelProperty("贡献度")
    val maintenance: Double = 0.0
)
