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

package com.tencent.bkrepo.common.metrics.push.custom.base

import com.tencent.bkrepo.common.metrics.push.custom.enums.DataModel
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary

class MetricsData(
    private var registry: CollectorRegistry,
    private var name: String = "",
    private var help: String = "",
    private val labels: MutableMap<String, String> = mutableMapOf(),
    private var keepHistory: Boolean = true,
    private var dataModel: DataModel? = null,
    private var counter: Counter? = null,
    private var gauge: Gauge? = null,
    private var histogram: Histogram? = null,
    private var summary: Summary? = null,
) {

    init {
        initialize()
    }

    fun setLabelValue(labels: MutableMap<String, String>) {
        labels.forEach(this::setLabelValue)
    }

    private fun setLabelValue(labelName: String, labelValue: String) {
        labels[labelName] = labelValue
    }

    /**
     * 更新值
     *
     * @param value
     */
    fun updateValue(value: Double) {
        var labelValues = arrayOf<String>()
        if (labels.isNotEmpty()) {
            labelValues = labels.values.toTypedArray()
        }
        when (dataModel) {
            DataModel.DATAMODEL_COUNTER -> updateCounter(labelValues, value)
            DataModel.DATAMODEL_GAUGE -> updateGauge(labelValues, value)
            DataModel.DATAMODEL_HISTOGRAM -> updateHistogram(labelValues, value)
            DataModel.DATAMODEL_SUMMARY -> updateSummary(labelValues, value)
            else -> {
            }
        }
    }

    fun clearHistory() {
        if (keepHistory) return
        when (dataModel) {
            DataModel.DATAMODEL_COUNTER -> clearHistoryCounter()
            DataModel.DATAMODEL_GAUGE -> clearHistoryGauge()
            DataModel.DATAMODEL_HISTOGRAM -> clearHistoryHistogram()
            DataModel.DATAMODEL_SUMMARY -> clearHistorySummary()
            else -> {
            }
        }
    }


    private fun initialize() {
        var labelNames = arrayOf<String>()
        if (labels.isNotEmpty()) {
            labelNames = labels.keys.toTypedArray()
        }
        when (dataModel) {
            DataModel.DATAMODEL_COUNTER -> createCounter(labelNames)
            DataModel.DATAMODEL_GAUGE -> createGauge(labelNames)
            DataModel.DATAMODEL_HISTOGRAM -> createHistogram(labelNames)
            DataModel.DATAMODEL_SUMMARY -> createSummary(labelNames)
            else -> {
            }
        }
    }

    private fun createCounter(labelNames: Array<String>) {
        counter = if (labelNames.isNotEmpty()) {
            Counter.build().name(name).help(help).labelNames(*labelNames)
                .register(registry)
        } else {
            Counter.build().name(name).help(help).register(registry)
        }
    }

    private fun createGauge(labelNames: Array<String>) {
        gauge = if (labelNames.isNotEmpty()) {
            Gauge.build().name(name).help(help).labelNames(*labelNames)
                .register(registry)
        } else {
            Gauge.build().name(name).help(help).register(registry)
        }
    }

    private fun createHistogram(labelNames: Array<String>) {
        histogram = if (labelNames.isNotEmpty()) {
            Histogram.build().name(name).help(help).labelNames(*labelNames)
                .register(registry)
        } else {
            Histogram.build().name(name).help(help).register(registry)
        }
    }

    private fun createSummary(labelNames: Array<String>) {
        summary = if (labelNames.isNotEmpty()) {
            Summary.build().name(name).help(help).labelNames(*labelNames)
                .register(registry)
        } else {
            Summary.build().name(name).help(help).register(registry)
        }
    }

    private fun updateCounter(labelValues: Array<String>, value: Double) {
        if (labelValues.isNotEmpty()) {
            counter?.labels(*labelValues)?.inc(value)
        } else {
            counter?.inc(value)
        }
    }

    private fun updateGauge(labelValues: Array<String>, value: Double) {
        if (labelValues.isNotEmpty()) {
            gauge?.labels(*labelValues)?.set(value)
        } else {
            gauge?.set(value)
        }
    }

    private fun updateHistogram(labelValues: Array<String>, value: Double) {
        if (labelValues.isNotEmpty()) {
            histogram?.labels(*labelValues)?.observe(value)
        } else {
            histogram?.observe(value)
        }
    }

    private fun updateSummary(labelValues: Array<String>, value: Double) {
        if (labelValues.isNotEmpty()) {
            summary?.labels(*labelValues)?.observe(value)
        } else {
            summary?.observe(value)
        }
    }

    private fun clearHistoryCounter() {
        if (keepHistory) return
        counter?.clear()
    }

    private fun clearHistoryGauge() {
        if (keepHistory) return
        gauge?.clear()
    }

    private fun clearHistoryHistogram() {
        if (keepHistory) return
        histogram?.clear()
    }

    private fun clearHistorySummary() {
        if (keepHistory) return
        summary?.clear()
    }
}
