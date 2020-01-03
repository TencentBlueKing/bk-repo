package com.tencent.bkrepo.opdata.pojo

import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import com.tencent.bkrepo.opdata.pojo.enums.MetricsType
import io.swagger.annotations.ApiModelProperty

data class Target(
    @ApiModelProperty("data")
    val data: Any?,
    @ApiModelProperty("target")
    val target: Metrics,
    @ApiModelProperty("refId")
    val refId: String,
    @ApiModelProperty("hide")
    val hide: Boolean,
    @ApiModelProperty("type")
    val type: MetricsType
)
