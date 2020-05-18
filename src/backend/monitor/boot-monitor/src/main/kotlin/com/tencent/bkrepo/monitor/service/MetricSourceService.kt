package com.tencent.bkrepo.monitor.service

import com.tencent.bkrepo.monitor.config.MonitorProperties
import com.tencent.bkrepo.monitor.metrics.MetricEndpoint
import com.tencent.bkrepo.monitor.metrics.MetricInfo
import de.codecentric.boot.admin.server.services.InstanceRegistry
import de.codecentric.boot.admin.server.web.client.InstanceWebClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import javax.annotation.PreDestroy

@Component
class MetricSourceService(
    private val monitorProperties: MonitorProperties,
    private val instanceRegistry: InstanceRegistry,
    private val instanceWebClient: InstanceWebClient
) {

    val metricSourceMap: MutableMap<MetricEndpoint, InstanceMetricSource> = mutableMapOf()

    init {
        monitorProperties.metrics.forEach { (metricName, applicationListString) ->
            val metricEndpoint = MetricEndpoint.ofMetricName(metricName)
            val trimmedApplicationListString = applicationListString.trim()
            val includeAll = trimmedApplicationListString.isEmpty() || trimmedApplicationListString == "*"
            val applicationList = trimmedApplicationListString.split(",").map { it.trim() }.distinct()
            val metricSource = InstanceMetricSource(metricEndpoint, includeAll, applicationList, monitorProperties.interval, instanceRegistry, instanceWebClient)
            metricSourceMap[metricEndpoint] = metricSource
        }
    }

    fun getMetricSource(metric: MetricEndpoint) = metricSourceMap[metric]?.metricsSource ?: Flux.empty()

    fun getMergedSource(): Flux<MetricInfo> {
        return Flux.merge(Flux.fromIterable(metricSourceMap.entries).map { it.value.metricsSource })
    }

    @PreDestroy
    private fun stop() {
        metricSourceMap.forEach { (_, source) -> source.stop() }
    }
}
