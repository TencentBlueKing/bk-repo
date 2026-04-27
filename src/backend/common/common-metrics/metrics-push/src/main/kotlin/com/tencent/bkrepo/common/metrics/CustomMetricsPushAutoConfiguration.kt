package com.tencent.bkrepo.common.metrics

import com.tencent.bkrepo.common.metrics.push.custom.CustomEventExporter
import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.BkHttpConnectionFactory
import com.tencent.bkrepo.common.metrics.push.custom.base.CustomEventPush
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusDrive
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusPush
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomEventConfig
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomPushConfig
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomReportConfig
import com.tencent.bkrepo.common.service.actuator.CommonTagProvider
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.PushGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusProperties
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusPushGatewayManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.net.MalformedURLException
import java.net.URL
import java.time.Duration

@Configuration
@EnableConfigurationProperties(CustomPushConfig::class, CustomEventConfig::class, CustomReportConfig::class)
class CustomMetricsPushAutoConfiguration {

    @Value(SERVICE_NAME)
    private lateinit var serviceName: String

    @Bean
    @ConditionalOnProperty(value = ["management.prometheus.metrics.export.pushgateway.enabled"])
    fun prometheusPushGatewayManager(
        collectorRegistry: CollectorRegistry?,
        prometheusProperties: PrometheusProperties,
        customPushConfig: CustomPushConfig,
    ): PrometheusPushGatewayManager? {
        val properties = prometheusProperties.pushgateway
        val pushRate: Duration = properties.pushRate
        val job = getJob(properties)
        val pushGateway: PushGateway = initializePushGateway(properties.baseUrl)
        pushGateway.setConnectionFactory(BkHttpConnectionFactory(token = customPushConfig.bktoken))
        val groupingKey = properties.groupingKey
        val shutdownOperation = properties.shutdownOperation
        return PrometheusPushGatewayManager(
            pushGateway, collectorRegistry, pushRate, job, groupingKey, shutdownOperation
        )
    }

    @Bean
    @ConditionalOnProperty(value = ["management.metrics.custom.enabled"])
    fun prometheusDrive(
        prometheusProperties: PrometheusProperties,
        customPushConfig: CustomPushConfig,
    ): PrometheusDrive {
        val properties = prometheusProperties.pushgateway
        val groupingKey = properties.groupingKey
        val job = getJob(properties)
        val pushDrive = PrometheusPush(
            job, groupingKey, properties.baseUrl,
            customPushConfig.bktoken, properties.username, properties.password
        )
        return PrometheusDrive(pushDrive = pushDrive)
    }

    /**
     * metrics 和 event 任一开启时均创建，drive 仅在 metrics 开启时注入，否则为 null（仅走 event 路径）。
     */
    @Bean
    @ConditionalOnExpression(
        "\${management.metrics.custom.enabled:false} || \${management.event.custom.enabled:false}"
    )
    fun customMetricsExporter(
        prometheusProperties: PrometheusProperties,
        taskScheduler: ThreadPoolTaskScheduler,
        customPushConfig: CustomPushConfig,
        customReportConfig: CustomReportConfig,
        drive: PrometheusDrive? = null,
        customEventExporter: CustomEventExporter? = null,
        customEventConfig: CustomEventConfig? = null,
        commonTagProvider: ObjectProvider<CommonTagProvider>,
    ): CustomMetricsExporter {
        return CustomMetricsExporter(
            customPushConfig = customPushConfig,
            customReportConfig = customReportConfig,
            prometheusProperties = prometheusProperties,
            scheduler = taskScheduler,
            customEventExporter = customEventExporter,
            customEventConfig = customEventConfig,
            drive = drive,
            commonTagProvider = commonTagProvider.ifAvailable,
        )
    }

    @Bean
    @ConditionalOnProperty(value = ["management.event.custom.enabled"])
    fun customEventPush(customEventConfig: CustomEventConfig): CustomEventPush {
        return CustomEventPush(customEventConfig)
    }

    @Bean
    @ConditionalOnProperty(value = ["management.event.custom.enabled"])
    fun customEventExporter(
        customEventConfig: CustomEventConfig,
        customEventPush: CustomEventPush,
        taskScheduler: ThreadPoolTaskScheduler,
    ): CustomEventExporter {
        return CustomEventExporter(customEventConfig, customEventPush, taskScheduler)
    }

    private fun initializePushGateway(url: String): PushGateway {
        return try {
            PushGateway(URL(url))
        } catch (ex: MalformedURLException) {
            logger.warn(
                "Invalid PushGateway base url '$url': update your configuration to a valid URL"
            )
            PushGateway(url)
        }
    }

    private fun getJob(properties: PrometheusProperties.Pushgateway): String {
        var job = properties.job
        job = job ?: serviceName
        return job
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomMetricsPushAutoConfiguration::class.java)
        private const val SERVICE_NAME = "\${service.prefix:}\${spring.application.name}\${service.suffix:}"
    }
}
