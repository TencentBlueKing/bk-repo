package com.tencent.bkrepo.monitor.export

import com.tencent.bkrepo.monitor.metrics.MetricInfo
import com.tencent.bkrepo.monitor.service.MetricSourceService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux

@Component
class InfluxMetricsExporter(
    metricSourceService: MetricSourceService,
    private val influxExportProperties: InfluxExportProperties
) {

    private val converter = InfluxMetricsConverter()
    private val webClient: WebClient
    private var databaseExists: Boolean = false
    private var influxEndpoint: String = ""
    init {
        with(influxExportProperties) {
            influxEndpoint = "/write?consistency=" + consistency.toLowerCase() + "&precision=ms&db=" + db
            retentionPolicy?.let { influxEndpoint += "&rp=$retentionPolicy" }

            val builder = WebClient.builder().baseUrl(uri)
            if (!username.isNullOrBlank()) {
                builder.defaultHeaders { it.setBasicAuth(username.orEmpty(), password.orEmpty()) }
            }
            webClient = builder.build()
            metricSourceService.getMergedSource().window(step).subscribe { exportMetricSource(it) }
        }
    }

    private fun exportMetricSource(metricSource: Flux<MetricInfo>) {
        if (!influxExportProperties.enabled) {
            return
        }
        createDatabaseIfNecessary()
        val stringSource = metricSource.map { converter.convert(it) }
        webClient.post()
            .uri(influxEndpoint)
            .contentType(MediaType.TEXT_PLAIN)
            .body(BodyInserters.fromPublisher(stringSource, String::class.java))
            .exchange()
            .flatMap { clientResponse ->
                clientResponse.bodyToMono(String::class.java)
                    .doOnSuccess {
                        if (clientResponse.statusCode().isError) {
                            logger.error("Failed to export metrics to influx")
                            logger.error("HttpStatusCode = {}", clientResponse.statusCode())
                            logger.error("HttpHeaders = {}", clientResponse.headers().asHttpHeaders())
                            logger.error("ResponseBody = {}", it)
                        }
                    }
            }.subscribe()
    }

    private fun createDatabaseIfNecessary() {
        if (!influxExportProperties.autoCreateDb || databaseExists) {
            return
        }
        val createDatabaseQuery = CreateDatabaseQueryBuilder(influxExportProperties.db)
            .setRetentionDuration(influxExportProperties.retentionDuration)
            .setRetentionPolicyName(influxExportProperties.retentionPolicy)
            .setRetentionReplicationFactor(influxExportProperties.retentionReplicationFactor)
            .setRetentionShardDuration(influxExportProperties.retentionShardDuration)
            .build()
        webClient.post()
            .uri("/query?q=$createDatabaseQuery")
            .exchange()
            .flatMap { clientResponse ->
                clientResponse.bodyToMono(String::class.java)
                    .doOnSuccess {
                        if (clientResponse.statusCode().isError) {
                            logger.error("Unable to create database '{}'", influxExportProperties.db)
                            logger.error("HttpStatusCode = {}", clientResponse.statusCode())
                            logger.error("HttpHeaders = {}", clientResponse.headers().asHttpHeaders())
                            logger.error("ResponseBody = {}", it)
                        } else {
                            logger.info("Influx database {} is ready to receive metrics", influxExportProperties.db)
                            databaseExists = true
                        }
                    }
            }.block()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InfluxMetricsExporter::class.java)
    }
}
