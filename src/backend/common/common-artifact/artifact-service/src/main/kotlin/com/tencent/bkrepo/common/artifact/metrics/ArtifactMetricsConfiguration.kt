/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics
import com.tencent.bkrepo.common.artifact.metrics.export.ArtifactMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.service.actuator.CommonTagProvider
import com.tencent.bkrepo.common.storage.config.StorageProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.autoconfigure.metrics.export.influx.InfluxProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    ArtifactMetrics::class,
    ArtifactWebMvcTagsContributor::class,
    ArtifactCacheMetrics::class,
    InstanceBandWidthMetrics::class,
)
@EnableConfigurationProperties(ArtifactMetricsProperties::class)
class ArtifactMetricsConfiguration {

    @Bean
    @ConditionalOnBean(InfluxProperties::class)
    fun influxMetricsExporter(
        influxProperties: InfluxProperties,
        commonTagProvider: ObjectProvider<CommonTagProvider>
    ): InfluxMetricsExporter {
        return InfluxMetricsExporter(influxProperties, commonTagProvider)
    }

    @Bean
    fun artifactTagProvider(
        storageProperties: StorageProperties,
        artifactMetricsProperties: ArtifactMetricsProperties
    ): ArtifactTransferTagProvider {
        return DefaultArtifactTagProvider(storageProperties, artifactMetricsProperties)
    }

    @Bean
    fun artifactMetricsExporter(
        customMetricsExporter: CustomMetricsExporter? = null,
        artifactMetricsProperties: ArtifactMetricsProperties,
    ): ArtifactMetricsExporter {
        return ArtifactMetricsExporter(customMetricsExporter, artifactMetricsProperties.allowUnknownProjectExport)
    }
}
