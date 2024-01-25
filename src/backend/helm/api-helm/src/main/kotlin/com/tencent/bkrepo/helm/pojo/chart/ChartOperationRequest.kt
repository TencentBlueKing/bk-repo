package com.tencent.bkrepo.helm.pojo.chart

open class ChartOperationRequest (
    val projectId: String,
    val repoName: String,
    val operator: String,
    val type: String
)
