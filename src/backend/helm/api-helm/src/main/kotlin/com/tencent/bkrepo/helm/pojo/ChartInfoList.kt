package com.tencent.bkrepo.helm.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("helm response")
class ChartInfoList constructor() {
    val apiVersion: String?= null
    val map: Map<String, Map<String, List<ChartInfo>?>?> = mutableMapOf()
    val generated: String? = null
    val serverInfo: List<String?> = mutableListOf()

    // constructor(
    //     apiVersion: String,
    //     map: Map<String, Map<String, List<ChartInfo>?>?>,
    //     generated: String,
    //     serverInfo: String
    //     ):this() {
    //     this.map =
    // }
}