package com.tencent.bkrepo.monitor.metrics

import io.micrometer.core.instrument.Statistic

data class Sample(
    val statistic: Statistic,
    val value: Double
)
