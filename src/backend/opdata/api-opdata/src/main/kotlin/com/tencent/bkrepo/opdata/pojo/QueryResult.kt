package com.tencent.bkrepo.opdata.pojo

import com.tencent.bkrepo.opdata.pojo.enums.MetricsType
import io.swagger.annotations.ApiModelProperty

data class QueryResult(
    @ApiModelProperty("columns")
    var columns: List<Columns>,
    @ApiModelProperty("rows")
    var rows: List<List<Any>>,
    @ApiModelProperty("metrics类型")
    var type: MetricsType
)
