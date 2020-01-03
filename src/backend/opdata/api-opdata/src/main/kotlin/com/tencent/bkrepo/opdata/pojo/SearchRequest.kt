package com.tencent.bkrepo.opdata.pojo

import com.tencent.bkrepo.opdata.pojo.enums.MetricsType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("search信息")
data class SearchRequest(
    @ApiModelProperty("查询类型")
    val type: MetricsType,
    @ApiModelProperty("查询目标")
    val target: String
)
