package com.tencent.bkrepo.helm.pojo.chart

interface ChartDeleteRequest {
    val projectId: String
    val repoName: String
    val operator: String
}
