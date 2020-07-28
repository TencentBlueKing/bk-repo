package com.tencent.bkrepo.monitor.service

import com.tencent.bkrepo.monitor.config.MonitorProperties
import com.tencent.bkrepo.monitor.metrics.HealthEndpoint
import com.tencent.bkrepo.monitor.metrics.HealthInfo
import de.codecentric.boot.admin.server.services.InstanceRegistry
import de.codecentric.boot.admin.server.web.client.InstanceWebClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import javax.annotation.PreDestroy

@Component
class HealthSourceService(
    private val monitorProperties: MonitorProperties,
    private val instanceRegistry: InstanceRegistry,
    instanceWebClientBuilder: InstanceWebClient.Builder
) {

    private val instanceWebClient = instanceWebClientBuilder.build()
    val healthSourceMap: MutableMap<HealthEndpoint, InstanceHealthSource> = mutableMapOf()

    init {
        monitorProperties.health.forEach { (healthName, applicationListString) ->
            val healthEndpoint = HealthEndpoint.ofHealthName(healthName)
            val trimmedApplicationListString = applicationListString.trim()
            val includeAll = trimmedApplicationListString.isEmpty() || trimmedApplicationListString == "*"
            val applicationList = trimmedApplicationListString.split(",").map { it.trim() }.distinct()
            val healthSource = InstanceHealthSource(healthEndpoint, includeAll, applicationList, monitorProperties.interval, instanceRegistry, instanceWebClient)
            healthSourceMap[healthEndpoint] = healthSource
        }
    }

    fun getHealthSource(health: HealthEndpoint) = healthSourceMap[health]?.healthSource ?: Flux.empty()

    fun getMergedSource(): Flux<HealthInfo> {
        return Flux.merge(Flux.fromIterable(healthSourceMap.entries).map { it.value.healthSource })
    }

    @PreDestroy
    private fun stop() {
        healthSourceMap.forEach { (_, source) -> source.stop() }
    }
}
