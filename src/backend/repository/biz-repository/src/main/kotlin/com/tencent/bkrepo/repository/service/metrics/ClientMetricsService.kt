package com.tencent.bkrepo.repository.service.metrics

import com.tencent.bkrepo.repository.pojo.metrics.ClientPushMetricsRequest
import com.tencent.bkrepo.repository.pojo.metrics.MetricsPushConfigResponse

interface ClientMetricsService {
    fun pushMetrics(
        request: ClientPushMetricsRequest,
        clientIp: String,
        userId: String
    ): MetricsPushConfigResponse
}
