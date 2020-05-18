package com.tencent.bkrepo.monitor.service

import com.tencent.bkrepo.monitor.metrics.HealthEndpoint
import com.tencent.bkrepo.monitor.metrics.HealthInfo
import com.tencent.bkrepo.monitor.metrics.HealthStatus
import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.services.InstanceRegistry
import de.codecentric.boot.admin.server.web.client.InstanceWebClient
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.UnicastProcessor
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Duration

class InstanceHealthSource(
    private val healthEndpoint: HealthEndpoint,
    private val includeAll: Boolean,
    private val applicationList: List<String>,
    interval: Duration,
    private val instanceRegistry: InstanceRegistry,
    private val instanceWebClient: InstanceWebClient
) {
    private val scheduler: Scheduler = Schedulers.newSingle(healthEndpoint.healthName)
    private val processor = UnicastProcessor.create<HealthInfo>()
    private val subscribe: Disposable
    val healthSource = processor.publish().autoConnect()

    init {
        subscribe = Flux.interval(interval)
            .map { logger.debug("Ready to check health[${healthEndpoint.healthName}]") }
            .flatMap { instanceRegistry.instances }
            .filter { it.isRegistered && (includeAll || applicationList.contains(it.registration.name)) }
            .subscribeOn(scheduler)
            .concatMap { updateHealthInfo(it) }
            .subscribe { processor.onNext(it) }
    }

    private fun updateHealthInfo(instance: Instance): Mono<HealthInfo> {
        return instanceWebClient.instance(instance).get()
            .uri(healthEndpoint.getEndpoint()).exchange()
            .flatMap { convert(it, instance) }
            .doOnError { logError(instance, it) }
            .onErrorResume { handleError(it) }
    }

    private fun convert(response: ClientResponse, instance: Instance): Mono<HealthInfo> {
        return response.bodyToMono(HealthStatus::class.java).map {
            HealthInfo(healthEndpoint.healthName, it, instance.registration.name, instance.id.value)
        }
    }

    private fun handleError(ex: Throwable): Mono<HealthInfo> {
        logger.error("error", ex)
        return Mono.empty()
    }

    private fun logError(instance: Instance, ex: Throwable) {
        if (instance.statusInfo.isOffline) {
            logger.debug("Couldn't retrieve health [${healthEndpoint.healthName}] for [$instance]", ex)
        } else {
            logger.warn("Couldn't retrieve health [${healthEndpoint.healthName}] for [$instance]", ex)
        }
    }

    fun stop() {
        scheduler.dispose()
        subscribe.dispose()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstanceMetricSource::class.java)
    }
}
