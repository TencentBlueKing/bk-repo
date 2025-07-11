/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metrics

import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.BkHttpConnectionFactory
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusDrive
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusPush
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomPushConfig
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.PushGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusProperties
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusPushGatewayManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.net.MalformedURLException
import java.net.URL
import java.time.Duration

/**
 * https://prometheus.io/docs/practices/pushing/#should-i-be-using-the-pushgateway
 * 官方推荐使用拉的方式
 * 目前只针对特殊指标
 */

@Configuration
@EnableConfigurationProperties(CustomPushConfig::class)
class CustomMetricsPushAutoConfiguration {


    @Value(SERVICE_NAME)
    private lateinit var serviceName: String

    @Bean
    @ConditionalOnProperty(value = ["management.metrics.export.prometheus.pushgateway.enabled"])
    fun prometheusPushGatewayManager(
        collectorRegistry: CollectorRegistry?,
        prometheusProperties: PrometheusProperties,
        customPushConfig: CustomPushConfig
    ): PrometheusPushGatewayManager? {
        val properties = prometheusProperties.pushgateway
        val pushRate: Duration = properties.pushRate
        val job = getJob(properties)
        val pushGateway: PushGateway = initializePushGateway(properties.baseUrl)
        pushGateway.setConnectionFactory(BkHttpConnectionFactory(token = customPushConfig.bktoken)) // 蓝鲸监控的Token
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
        customPushConfig: CustomPushConfig
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

    @Bean
    @ConditionalOnProperty(value = ["management.metrics.custom.enabled"])
    fun customMetricsExporter(
        drive: PrometheusDrive,
        prometheusProperties: PrometheusProperties,
        taskScheduler: ThreadPoolTaskScheduler,
        customPushConfig: CustomPushConfig,
    ): CustomMetricsExporter {
        return CustomMetricsExporter(customPushConfig, CollectorRegistry(), drive, prometheusProperties, taskScheduler)
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
