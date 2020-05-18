package com.tencent.bkrepo.monitor.controller

import com.tencent.bkrepo.monitor.metrics.MetricEndpoint
import com.tencent.bkrepo.monitor.metrics.MetricInfo
import com.tencent.bkrepo.monitor.service.MetricSourceService
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/monitor")
class InstanceMetricsController(val metricSourceService: MetricSourceService) {

    @GetMapping(path = ["/metrics/{metricName}"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun metricsStream(@PathVariable metricName: String): Flux<MetricInfo> {
        val metricEndpoint = MetricEndpoint.ofMetricName(metricName)
        return metricSourceService.getMetricSource(metricEndpoint)
    }

    @GetMapping(path = ["/metrics"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun metricsStream(): Flux<ServerSentEvent<MetricInfo>> {
        return metricSourceService.getMergedSource().map { transformServerSendEvent(it) }
    }

    private fun transformServerSendEvent(metricInfo: MetricInfo): ServerSentEvent<MetricInfo> {
        return ServerSentEvent.builder(metricInfo)
            .event(metricInfo.name)
            .build()
    }
}
