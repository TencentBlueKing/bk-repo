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
import com.tencent.bkrepo.common.metrics.push.custom.MetricsDataManager.removeEmptyMetrics
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusDrive
import io.prometheus.client.CollectorRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

class ScheduleMetricsExporter(
    private val registry: CollectorRegistry,
    private val drive: PrometheusDrive,
    private val scheduler: ThreadPoolTaskScheduler,
    pushRate: Duration = Duration.ofSeconds(30),
) {
    var queue: ConcurrentLinkedQueue<MetricsItem> = ConcurrentLinkedQueue()

    init {
        scheduler.scheduleAtFixedRate(this::exportMetricsData, pushRate)
    }

    private fun exportMetricsData() {
        if (queue.isEmpty()) {
            return
        }
        logger.debug("start to export metric data to prometheus server")
        val count = queue.size
        for (i in 0 until count) {
            val item: MetricsItem = queue.poll()
            try {
                var data = MetricsDataManager.getMetricsData(item.name)
                if (data == null) {
                    data = MetricsDataManager.createMetricsData(
                        item.name, item.help, item.keepHistory, item.dataModel, item.labels, registry
                    )
                }
                data.setLabelValue(item.labels)
                data.updateValue(item.value)
            } catch (e: Exception) {
                logger.warn("set metrics for item $item error: ${e.message}")
            }
        }
        // 上报前清除没有数据的指标
        removeEmptyMetrics(registry)
        logger.debug("metrics: ${registry.metricFamilySamples().iterator().toJsonString()}")
        drive.push(registry)
        // 不管是否成功都清除，避免异常情况下占用内存过多
        MetricsDataManager.clearMetricsHistory()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduleMetricsExporter::class.java)
    }
}
