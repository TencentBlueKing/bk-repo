package com.tencent.bkrepo.common.query.model

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("分页参数")
data class PageLimit(
    @ApiModelProperty("当前页(第0页开始)")
    val current: Int = 0,
    @ApiModelProperty("每页数量")
    val size: Int = 20
)
