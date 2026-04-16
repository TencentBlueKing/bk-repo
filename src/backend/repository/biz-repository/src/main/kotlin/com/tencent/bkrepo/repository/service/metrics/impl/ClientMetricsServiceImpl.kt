package com.tencent.bkrepo.repository.service.metrics.impl

import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.enums.DataModel
import com.tencent.bkrepo.repository.config.MetricsPushProperties
import com.tencent.bkrepo.repository.pojo.metrics.ClientPushMetricsRequest
import com.tencent.bkrepo.repository.pojo.metrics.MetricsPushConfigResponse
import com.tencent.bkrepo.repository.service.metrics.ClientMetricsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ClientMetricsServiceImpl(
    private val metricsPushProperties: MetricsPushProperties,
    private val customMetricsExporter: CustomMetricsExporter? = null
) : ClientMetricsService {

    override fun pushMetrics(
        request: ClientPushMetricsRequest,
        clientIp: String,
        userId: String
    ): MetricsPushConfigResponse {
        val enabledForUser = metricsPushProperties.isEnabledForUser(userId)
        if (enabledForUser) {
            request.metrics.forEach {
                val newLabels = mutableMapOf<String, String>()
                newLabels.putAll(it.labels)
                newLabels["clientIp"] = clientIp
                try {
                    val metricItem = MetricsItem(
                        it.metricName, it.metricHelp, DataModel.valueOf(it.metricDataModel),
                        it.keepHistory, it.value.toDouble(), newLabels
                    )
                    customMetricsExporter?.reportMetrics(metricItem)
                } catch (e: Exception) {
                    logger.warn("Failed to push metric[${it.metricName}]: ${e.message}")
                }
            }
        }
        return MetricsPushConfigResponse(
            enabled = enabledForUser,
            intervalSeconds = metricsPushProperties.intervalSeconds,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientMetricsServiceImpl::class.java)
    }
}
