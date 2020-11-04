package com.tencent.bkrepo.helm.listener.event

import com.tencent.bkrepo.helm.pojo.chart.ChartDeleteRequest

data class ChartDeleteEvent(val request: ChartDeleteRequest)