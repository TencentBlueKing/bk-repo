package com.tencent.bkrepo.helm.listener.event

import com.tencent.bkrepo.helm.pojo.chart.ChartVersionDeleteRequest

data class ChartVersionDeleteEvent(val request: ChartVersionDeleteRequest)
