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

package com.tencent.bkrepo.common.metrics.push.custom

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusDrive
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusPushSource
import io.prometheus.client.CollectorRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentLinkedQueue

class ScheduleMetricsExporter(
    private val drive: PrometheusDrive,
    private val scheduler: ThreadPoolTaskScheduler,
    pushRate: Duration = Duration.ofSeconds(30),
    private val labelIncludes: Map<String, List<String>> = emptyMap(),
) {
    var queue: ConcurrentLinkedQueue<MetricsItem> = ConcurrentLinkedQueue()
    private val sourceExporters = drive.sources().associateWith { SourceExporter(it) }

    init {
        scheduler.scheduleAtFixedRate(this::exportMetricsData, pushRate)
    }

    private fun exportMetricsData() {
        if (queue.isEmpty()) {
            return
        }
        logger.debug("start to export metric data to prometheus server")
        val items = normalizeMetricLabels(drainQueue())
        sourceExporters.forEach { (source, exporter) ->
            val sourceItems = items.filter { source.supports(it.name) }
                .map { pruneLabelsForSource(it, source) }
            exporter.export(sourceItems)
        }
    }

    private fun pruneLabelsForSource(item: MetricsItem, source: PrometheusPushSource): MetricsItem {
        val keepKeys = when {
            source.labelIncludes.containsKey(item.name) -> source.labelIncludes[item.name]
            labelIncludes.containsKey(item.name) -> labelIncludes[item.name]
            else -> null
        } ?: return item
        if (ALL_LABELS in keepKeys) return item
        return item.copy(labels = item.labels.filterKeys { it in keepKeys })
    }

    private inner class SourceExporter(private val source: PrometheusPushSource) {
        private val registry = CollectorRegistry()
        private val dataManager = MetricsDataManager()

        fun export(items: List<MetricsItem>) {
            dataManager.removeEmptyMetrics(registry)
            if (items.isEmpty()) return
            items.forEach(::setMetricsData)
            // 上报前清除没有数据的指标
            dataManager.removeEmptyMetrics(registry)
            val metrics = registry.metricFamilySamples().iterator().toJsonString()
            logger.debug("metrics: $metrics")
            drive.push(source, registry)
            dataManager.clearMetricsHistory()
        }

        private fun setMetricsData(item: MetricsItem) {
            try {
                var data = dataManager.getMetricsData(item.name)
                if (data == null) {
                    data = dataManager.createMetricsData(
                        item.name, item.help, item.keepHistory, item.dataModel, item.labels, registry
                    )
                }
                data.setLabelValue(item.labels)
                data.updateValue(item.value)
            } catch (e: Exception) {
                logger.warn("set metrics for item $item error: ${e.message}")
            }
        }
    }

    private fun drainQueue(): List<MetricsItem> {
        val items = mutableListOf<MetricsItem>()
        val count = queue.size
        for (i in 0 until count) {
            queue.poll()?.let(items::add)
        }
        return items
    }

    private fun normalizeMetricLabels(items: List<MetricsItem>): List<MetricsItem> {
        val labelKeysByMetric = linkedMapOf<String, LinkedHashSet<String>>()
        items.forEach { item ->
            val keys = labelKeysByMetric.getOrPut(item.name) { LinkedHashSet() }
            item.labels.keys.forEach(keys::add)
        }
        return items.map { item ->
            val labelKeys = labelKeysByMetric[item.name] ?: return@map item
            if (item.labels.keys == labelKeys) {
                return@map item
            }
            val normalizedLabels = LinkedHashMap<String, String>(labelKeys.size)
            labelKeys.forEach { key ->
                normalizedLabels[key] = item.labels[key].orEmpty()
            }
            item.copy(labels = normalizedLabels)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduleMetricsExporter::class.java)
        private const val ALL_LABELS = "*"
    }
}
