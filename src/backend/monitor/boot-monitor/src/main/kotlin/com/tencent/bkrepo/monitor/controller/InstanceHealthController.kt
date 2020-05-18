package com.tencent.bkrepo.monitor.controller

import com.tencent.bkrepo.monitor.metrics.HealthEndpoint
import com.tencent.bkrepo.monitor.metrics.HealthInfo
import com.tencent.bkrepo.monitor.service.HealthSourceService
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/monitor")
class InstanceHealthController(val healthSourceService: HealthSourceService) {

    @GetMapping(path = ["/health/{healthName}"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun metricsStream(@PathVariable healthName: String): Flux<HealthInfo> {
        val healthEndpoint = HealthEndpoint.ofHealthName(healthName)
        return healthSourceService.getHealthSource(healthEndpoint)
    }

    @GetMapping(path = ["/health"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun metricsStream(): Flux<ServerSentEvent<HealthInfo>> {
        return healthSourceService.getMergedSource().map { transformServerSendEvent(it) }
    }

    private fun transformServerSendEvent(healthInfo: HealthInfo): ServerSentEvent<HealthInfo> {
        return ServerSentEvent.builder(healthInfo)
            .event(healthInfo.name)
            .build()
    }
}
