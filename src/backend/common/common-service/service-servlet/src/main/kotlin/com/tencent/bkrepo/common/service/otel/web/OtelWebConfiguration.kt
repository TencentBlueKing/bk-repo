/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.service.otel.web

import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.cloud.sleuth.autoconfig.otel.OtelProcessorProperties
import org.springframework.cloud.sleuth.autoconfig.otel.SpanProcessorProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnProperty(value = ["spring.sleuth.enabled"], matchIfMissing = true)
class OtelWebConfiguration {

    @Bean
    fun otelWebFilter(): FilterRegistrationBean<OtelWebFilter> {
        val registrationBean = FilterRegistrationBean<OtelWebFilter>()
        registrationBean.filter = OtelWebFilter()
        registrationBean.order = Ordered.HIGHEST_PRECEDENCE
        registrationBean.addUrlPatterns("/*")
        return registrationBean
    }

    @Bean
    @ConditionalOnMissingBean
    fun otelBatchSpanProcessorProvider(otelProcessorProperties: OtelProcessorProperties): SpanProcessorProvider {
        return object : SpanProcessorProvider {
            override fun toSpanProcessor(spanExporter: SpanExporter): SpanProcessor {
                val builder = BatchSpanProcessor.builder(spanExporter)
                setBuilderProperties(otelProcessorProperties, builder)
                return builder.build()
            }

            fun setBuilderProperties(
                otelProcessorProperties: OtelProcessorProperties,
                builder: BatchSpanProcessorBuilder
            ) {
                if (otelProcessorProperties.batch.exporterTimeout != null) {
                    builder.setExporterTimeout(
                        otelProcessorProperties.batch.exporterTimeout,
                        TimeUnit.MILLISECONDS
                    )
                }
                if (otelProcessorProperties.batch.maxExportBatchSize != null) {
                    builder.setMaxExportBatchSize(otelProcessorProperties.batch.maxExportBatchSize)
                }
                if (otelProcessorProperties.batch.maxQueueSize != null) {
                    builder.setMaxQueueSize(otelProcessorProperties.batch.maxQueueSize)
                }
                if (otelProcessorProperties.batch.scheduleDelay != null) {
                    builder.setScheduleDelay(
                        otelProcessorProperties.batch.scheduleDelay,
                        TimeUnit.MILLISECONDS
                    )
                }
            }
        }
    }
}
