package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModelProperty

data class QueryRequest(
//    @ApiModelProperty("requestId")
//    val requestId: String,
//    @ApiModelProperty("timezone")
//    val timezone: String,
//    @ApiModelProperty("panelId")
//    val panelId: Int,
//    @ApiModelProperty("dashboardId")
//    val dashboardId: Int,
//    @ApiModelProperty("range")
//    val range: Range,
//    @ApiModelProperty("interval")
//    val interval: String,
//    @ApiModelProperty("intervalMs")
//    val intervalMs: Long,
//    @ApiModelProperty("maxDataPoints")
//    val maxDataPoints: Long,
    @ApiModelProperty("targets")
    val targets: List<Target>
//    @ApiModelProperty("scopedVars")
//    val scopedVars: List<Target>,
//    @ApiModelProperty("startTime")
//    val startTime: Long,
//    @ApiModelProperty("rangeRaw")
//    val rangeRaw: Raw,
//    @ApiModelProperty("adhocFilters")
//    val adhocFilters: List<Filter>
)
