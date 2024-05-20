/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metrics.push.custom

import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsData
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsDataBuilder
import com.tencent.bkrepo.common.metrics.push.custom.enums.DataModel
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.ConcurrentHashMap


object MetricsDataManager {

    private val metricsDataCache: ConcurrentHashMap<String, MetricsData> = ConcurrentHashMap()

    fun getMetricsData(metricsName: String): MetricsData? {
        return metricsDataCache[metricsName]
    }

    fun createMetricsData(
        name: String = "",
        help: String = "",
        keepHistory: Boolean = true,
        dataModel: DataModel = DataModel.DATAMODEL_GAUGE,
        labels: MutableMap<String, String>,
        registry: CollectorRegistry
    ): MetricsData {
        val metricsData = MetricsDataBuilder(registry)
            .name(name)
            .help(help)
            .keepHistory(keepHistory)
            .labels(labels)
            .dataModel(dataModel)
            .buildMetricData()
        metricsDataCache[name] = metricsData
        return metricsData
    }

    fun clearMetricsHistory() {
        metricsDataCache.values.forEach {
            it.clearHistory()
        }
    }

    fun removeEmptyMetrics(registry: CollectorRegistry) {
        val iterator = metricsDataCache.entries.iterator()
        while (iterator.hasNext()) {
            val metric = iterator.next()
            if (metric.value.noLabelsChild()) {
                registry.unregister(metric.value.getCollector())
                iterator.remove()
            }
        }
    }
}
