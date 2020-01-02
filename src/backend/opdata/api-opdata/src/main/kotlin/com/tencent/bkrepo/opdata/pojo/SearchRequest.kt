package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import com.tencent.bkrepo.opdata.pojo.enums.Type

@ApiModel("search信息")
data class SearchRequest(
    @ApiModelProperty("查询类型")
    val type: Type,
    @ApiModelProperty("查询目标")
    val target: String
)
